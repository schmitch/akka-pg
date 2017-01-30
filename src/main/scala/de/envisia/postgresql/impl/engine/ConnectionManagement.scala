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

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

private[engine] class ConnectionManagement(engine: EngineVar, bufferSize: Int = 100, reconnectTimeout: FiniteDuration = 5.seconds)(implicit actorSystem: ActorSystem, mat: Materializer) extends GraphStage[FlowShape[PostgreClientMessage, PostgreServerMessage]] {
  private val in = Inlet[PostgreClientMessage]("ConnectionManagement.in")
  private val out = Outlet[PostgreServerMessage]("ConnectionManagement.out")

  override val shape: FlowShape[PostgreClientMessage, PostgreServerMessage] = FlowShape.of(in, out)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private implicit val ec = mat.executionContext

    private var source: Future[SourceQueueWithComplete[PostgreClientMessage]] = null
    private var sink: Future[SinkQueueWithCancel[PostgreServerMessage]] = null
    private def grabElement = {
      sink.flatMap(_.pull())
    }

    private val decider: Supervision.Decider = { t =>
      debug(s"Debug: $t")
      Supervision.Resume
    }

    private def newPromise(): Promise[SourceQueueWithComplete[PostgreClientMessage]] = {
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

    private def connect(): Unit = {
      debug("Connect to PostgreSQL")
      val ip = newPromise()
      val sp = newSinkPromise()
      val (source, sink) = Source.queue(bufferSize, OverflowStrategy.fail)
          .via(connectionFlow)
          .toMat(Sink.queue())(Keep.both)
          .withAttributes(ActorAttributes.supervisionStrategy(decider))
          .run()

      source.watchCompletion().onComplete {
        case Success(_) =>
          debug("Source success")
          mat.scheduleOnce(reconnectTimeout, new Runnable {
            override def run(): Unit = connect()
          })
        case Failure(t) =>
          debug(s"Source Failed $t")
          mat.scheduleOnce(reconnectTimeout, new Runnable {
            override def run(): Unit = connect()
          })
      }

      ip.success(source)
      sp.success(sink)
    }

    override def preStart(): Unit = {
      connect()
      pull(in)
    }

    private def call(callback: AsyncCallback[Try[Option[PostgreServerMessage]]]) = {
      val elem = grabElement.map(Success(_)).recoverWith {
        // FIXME: how to still invoke the handler in a correct manner
        case NonFatal(t) =>
          // we need to reconnect, maybe
          //          debug(s"Fail: $t")
          Future.successful(Failure(t))
      }
      elem.foreach(callback.invoke)
    }

    def newCallback: AsyncCallback[Try[Option[PostgreServerMessage]]] = {
      getAsyncCallback[Try[Option[PostgreServerMessage]]] {
        case Success(s) => s match {
          case Some(msg) => push(out, msg)
          case None => call(newCallback)
        }
        case Failure(t) => fail(out, t)
      }
    }

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        debug("onPull")
        call(newCallback)
      }

      override def onDownstreamFinish(): Unit = {
        debug("in1,out1, onDownstreamFinish()")
        super.onDownstreamFinish()
      }
    })

    private def puller(): Unit = {
      debug(s"New Pull")
      pull(in)
    }

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        debug("onPush")
        val ele = grab(in)
        try {
          // fixme
          val callback = getAsyncCallback[QueueOfferResult] {
            case QueueOfferResult.Enqueued => puller()
            case QueueOfferResult.Dropped => puller()
            case QueueOfferResult.Failure(t) => debug(s"Before Pull Fail: $t"); puller()
            case QueueOfferResult.QueueClosed => puller()
          }
          source.flatMap(_.offer(ele)).foreach(callback.invoke)
        } catch {
          case NonFatal(t) => debug(s"NonFatal onPush: $t")
        }
      }

      override def onUpstreamFinish(): Unit = {
        debug("in1,out1, onUpstreamFinish()")
        completeStage()
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        debug(s"onUpstreamFailure, $ex")
        super.onUpstreamFailure(ex)
      }
    })

    private def debug(msg: String): Unit = {
      println(msg)
    }

  }

}
