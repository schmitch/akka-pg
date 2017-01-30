/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source, SourceQueueWithComplete, Tcp}
import akka.stream.stage._
import de.envisia.postgresql.message.backend.PostgreServerMessage
import de.envisia.postgresql.message.frontend.QueryMessage

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

private[engine] class ConnectionManagement(
    engine: EngineVar,
    bufferSize: Int = 100,
    reconnectTimeout: FiniteDuration = 5.seconds,
    failureTimeout: FiniteDuration = 1.second
)(implicit actorSystem: ActorSystem, mat: Materializer)
    extends GraphStageWithMaterializedValue[FlowShape[PostgreClientMessage, PostgreServerMessage], () => Future[ConnectionState]] {

  private val in = Inlet[PostgreClientMessage]("ConnectionManagement.in")
  private val out = Outlet[PostgreServerMessage]("ConnectionManagement.out")
  private var promise = Promise[ConnectionState]()

  override val shape: FlowShape[PostgreClientMessage, PostgreServerMessage] = FlowShape.of(in, out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, () => Future[ConnectionState]) = {
    val stage = new GraphStageLogic(shape) {

      private implicit val ec = mat.executionContext

      private val replay: mutable.Buffer[QueryMessage] = mutable.Buffer()
      private var source: Future[SourceQueueWithComplete[PostgreClientMessage]] = _
      private var sink: Future[SinkQueueWithCancel[PostgreServerMessage]] = _
      private def grabElement = {
        sink.flatMap(_.pull()).recover{
          case NonFatal(e) => println(s"NONFATAL: $e"); throw e
        }
      }

      private val decider: Supervision.Decider = { t =>
        debug(s"Debug: $t")
        Supervision.Resume
      }

      private def newSourcePromise(): Promise[SourceQueueWithComplete[PostgreClientMessage]] = {
        val sourcePromise = Promise[SourceQueueWithComplete[PostgreClientMessage]]()
        source = sourcePromise.future
        sourcePromise
      }

      private def newSinkPromise(): Promise[SinkQueueWithCancel[PostgreServerMessage]] = {
        val sinkPromise = Promise[SinkQueueWithCancel[PostgreServerMessage]]()
        sink = sinkPromise.future
        sinkPromise
      }

      private def connectionFlow = {
        // TODO: Built Authentication flow i.e. startup / password around
        Fusing.aggressive(Tcp().outgoingConnection(InetSocketAddress.createUnresolved(engine.host, engine.port), connectTimeout = engine.timeout)
            .join(new PostgreProtocol(StandardCharsets.UTF_8).serialization)
            .join(new PostgreStage(engine.database, engine.username, engine.password)))
      }

      private def reconnect(
          oldIp: Option[Promise[SourceQueueWithComplete[PostgreClientMessage]]] = None,
          oldSp: Option[Promise[SinkQueueWithCancel[PostgreServerMessage]]] = None
      ): Unit = {
        mat.scheduleOnce(reconnectTimeout, new Runnable {
          override def run(): Unit = {
            promise = Promise()
            val ip = oldIp.getOrElse(newSourcePromise())
            val sp = oldSp.getOrElse(newSinkPromise())
            connect(promise, ip, sp)
          }
        })
      }

      private def connect(
          p: Promise[ConnectionState],
          ip: Promise[SourceQueueWithComplete[PostgreClientMessage]],
          sp: Promise[SinkQueueWithCancel[PostgreServerMessage]]
      ): Unit = {
        debug("Connect to PostgreSQL")
        val ((source, connection), sink) = Source.queue(bufferSize, OverflowStrategy.fail)
            .viaMat(connectionFlow)(Keep.both)
            .toMat(Sink.queue())(Keep.both)
            .withAttributes(ActorAttributes.supervisionStrategy(decider))
            .run()

        // Replay
        if (replay.nonEmpty) {
          println(s"Buffer Size: ${replay.size}")
          replay.foreach(qm => source.offer(qm))
        }

        connection.onComplete {
          case Success(connected) =>
            p.success(ConnectionState.Connected)
            debug(s"Connected to: $connected")
            source.watchCompletion().onComplete {
              case Success(_) =>
                promise = Promise()
                promise.success(ConnectionState.Disconnected)
                debug("Source success")
                reconnect()
              case Failure(t) =>
                promise = Promise()
                promise.success(ConnectionState.Disconnected)
                debug(s"Source Failed $t")
                reconnect()
            }
            ip.success(source)
            sp.success(sink)
          case Failure(failure) =>
            p.failure(failure)
            debug(s"Failure: $failure")
            // on failure we just never execute the promise,
            // so that the callback will block until we have a connection
            reconnect(Some(ip), Some(sp))
        }
      }

      override def preStart(): Unit = {
        connect(promise, newSourcePromise(), newSinkPromise())
        pull(in)
      }

      def newCallback: AsyncCallback[Option[PostgreServerMessage]] = {
        // if we run into no elem, we just need to create a new callback
        // since our stream **never** terminates
        getAsyncCallback[Option[PostgreServerMessage]] {
          case Some(msg) => debug("Push Element"); push(out, msg)
          case None => debug("no elem"); grabElement.foreach(newCallback.invoke)
        }
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          debug("onPull")
          grabElement.foreach(newCallback.invoke)
        }

        override def onDownstreamFinish(): Unit = {
          // FIXME: complete our underlying stream
          debug("in1,out1, onDownstreamFinish()")
          super.onDownstreamFinish()
        }
      })

      private def enqueue(ele: PostgreClientMessage, callback: AsyncCallback[QueueOfferResult]) = {
        source.flatMap(_.offer(ele)).foreach(callback.invoke)
      }

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          debug("onPush")
          val ele = grab(in)
          ele match {
            case qm: QueryMessage if qm.query.startsWith("LISTEN") => replay += qm
            case _ =>
          }

          try {
            // FIXME: fix the underlying implementation
            val callback = getAsyncCallback[QueueOfferResult] {
              case QueueOfferResult.Enqueued => debug(s"New Pull Enqueued"); pull(in)
              case QueueOfferResult.Dropped => debug(s"New Pull Dropped"); fail(out, new Exception("Dropped"))
              case QueueOfferResult.Failure(t) => debug(s"Before Pull Fail: $t"); fail(out, t)
              case QueueOfferResult.QueueClosed => debug(s"New Pull QueueClosed"); fail(out, new Exception("QueueClosed"))
            }
            enqueue(ele, callback)
          } catch {
            case NonFatal(t) => debug(s"NonFatal onPush: $t")
          }
        }

        override def onUpstreamFinish(): Unit = {
          // FIXME: complete our underlying stream
          debug("in1,out1, onUpstreamFinish()")
          completeStage()
        }

        override def onUpstreamFailure(ex: Throwable): Unit = {
          // FIXME: fail our underlying stream
          debug(s"onUpstreamFailure, $ex")
          super.onUpstreamFailure(ex)
        }
      })

      private def debug(msg: String): Unit = {
        println(msg)
      }

    }

    (stage, () =>  promise.future)
  }
}