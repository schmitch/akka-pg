/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ Keep, Sink, SinkQueueWithCancel, Source, SourceQueueWithComplete, Tcp }
import akka.stream.stage._
import de.envisia.postgresql.codec._
import de.envisia.postgresql.message.frontend.QueryMessage
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

private[engine] class ConnectionManagement(
  engine: EngineVar,
  bufferSize: Int = 100,
  reconnectTimeout: FiniteDuration = 5.seconds,
  failureTimeout: FiniteDuration = 1.second
)(implicit actorSystem: ActorSystem, mat: Materializer)
    extends GraphStageWithMaterializedValue[FlowShape[InMessage, OutMessage], () => Future[Tcp.OutgoingConnection]] {

  private val logger = LoggerFactory.getLogger(classOf[ConnectionManagement])

  private val in = Inlet[InMessage]("ConnectionManagement.in")
  private val out = Outlet[OutMessage]("ConnectionManagement.out")
  private var promise = Promise[Tcp.OutgoingConnection]()

  override val shape: FlowShape[InMessage, OutMessage] = FlowShape.of(in, out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, () => Future[Tcp.OutgoingConnection]) = {
    val stage = new GraphStageLogic(shape) {

      private implicit val ec = mat.executionContext

      private val replay: mutable.Buffer[QueryMessage] = mutable.Buffer()
      private var source: Future[SourceQueueWithComplete[InMessage]] = _
      private var sink: Future[SinkQueueWithCancel[OutMessage]] = _

      private val decider: Supervision.Decider = { t =>
        debug(s"Debug: $t")
        Supervision.Resume
      }

      private def newSourcePromise(): Promise[SourceQueueWithComplete[InMessage]] = {
        val sourcePromise = Promise[SourceQueueWithComplete[InMessage]]()
        source = sourcePromise.future
        sourcePromise
      }

      private def newSinkPromise(): Promise[SinkQueueWithCancel[OutMessage]] = {
        val sinkPromise = Promise[SinkQueueWithCancel[OutMessage]]()
        sink = sinkPromise.future
        sinkPromise
      }

      private def grabElement: Future[Option[OutMessage]] = {
        sink.flatMap(_.pull()).recover {
          case NonFatal(e) => debug(s"FATAL: $e"); throw e
        }
      }

      private def clear(ele: Dispatch, t: Throwable) = {
        // clears any outstanding promises
        ele match {
          case ReturnDispatch(_, p) =>
            if (!p.isCompleted) {
              p.failure(t)
            }
          case _ =>
        }
      }

      private def enqueue(ele: InMessage, callback: AsyncCallback[QueueOfferResult]) = {
        source.flatMap(_.offer(ele)).onComplete {
          case Success(s) => callback.invoke(s)
          // if there is a failure and the element has a promise, we can fail it
          case Failure(t) =>
            clear(ele, t)
            debug(s"Enqueue Failure $t")
            None
        }
      }

      private def checkIfReplay(ele: InMessage): Unit = {
        def addReplay(s: PostgreClientMessage) = {
          s match {
            case qm: QueryMessage if qm.query.startsWith("LISTEN") => replay += qm
            case _ =>
          }
        }
        ele match {
          case SimpleDispatch(s) => addReplay(s)
          case ReturnDispatch(s, _) => addReplay(s)
        }
      }

      private def connectionFlow = {
        // TODO: Built Authentication flow i.e. startup / password around
        Tcp().outgoingConnection(InetSocketAddress.createUnresolved(engine.host, engine.port), connectTimeout = engine.timeout)
          .join(new PostgreProtocol(StandardCharsets.UTF_8).serialization)
          .join(new PostgreStage(engine.database, engine.username, engine.password))
      }

      private def reconnect(spOpt: Option[Promise[SinkQueueWithCancel[OutMessage]]] = None): Unit = {
        promise = Promise()
        val ip = newSourcePromise()
        val sp = spOpt.getOrElse(newSinkPromise())
        mat.scheduleOnce(reconnectTimeout, new Runnable {
          override def run(): Unit = {
            connect(promise, ip, sp)
          }
        })
      }

      private def connect(
        p: Promise[Tcp.OutgoingConnection],
        ip: Promise[SourceQueueWithComplete[InMessage]],
        sp: Promise[SinkQueueWithCancel[OutMessage]]
      ): Unit = {
        debug("Connect to PostgreSQL")
        val ((source, connection), sink) = Source.queue(bufferSize, OverflowStrategy.fail)
          .viaMat(connectionFlow)(Keep.both)
          .toMat(Sink.queue())(Keep.both)
          .withAttributes(ActorAttributes.supervisionStrategy(decider))
          .run()

        connection.onComplete {
          case Success(connected) =>
            debug(s"Connected to: $connected")
            p.success(connected)
            ip.success(source)
            sp.success(sink)
            // Replay
            if (replay.nonEmpty) {
              debug(s"Replay Size: ${replay.size}")
              replay.foreach(qm => source.offer(SimpleDispatch(qm)))
            }
          case Failure(failure) =>
            debug(s"Failure: $failure")
            p.failure(failure)
            // fail the source queue, so that new requests will fail
            ip.failure(failure)
            // on failure we just never execute the promise,
            // so that the callback will block until we have a connection
            reconnect(Some(sp))
        }
      }

      override def preStart(): Unit = {
        connect(promise, newSourcePromise(), newSinkPromise())
        pull(in)
      }

      def newCallback: AsyncCallback[Option[OutMessage]] = {
        // if we run into no elem, we just need to create a new callback
        // since our stream **never** terminates
        getAsyncCallback[Option[OutMessage]] {
          case Some(msg) =>
            debug("Push Element"); push(out, msg)
          case None =>
            // this means that the stream was disconnected
            // so we need to reconnect it
            reconnect()
            grabElement.foreach(newCallback.invoke)
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

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          debug("onPush")
          val ele = grab(in)
          checkIfReplay(ele)
          try {
            // FIXME: fix the underlying implementation
            val callback = getAsyncCallback[QueueOfferResult] {
              case QueueOfferResult.Enqueued =>
                debug(s"New Pull Enqueued"); pull(in)
              case QueueOfferResult.Dropped =>
                debug(s"New Pull Dropped"); fail(out, new Exception("Dropped"))
              case QueueOfferResult.Failure(t) =>
                debug(s"Before Pull Fail: $t"); fail(out, t)
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
        logger.debug(msg)
      }

    }

    (stage, () => promise.future)
  }
}