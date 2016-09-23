/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine2

import java.nio.charset.StandardCharsets

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ BidiFlow, Broadcast, Flow, GraphDSL, Keep, Merge, RunnableGraph, Sink, Source, SourceQueueWithComplete, Tcp }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import de.envisia.postgresql.impl.engine.PostgreProtocol
import de.envisia.postgresql.message.backend.{ AuthenticationChallengeMD5, PostgreServerMessage, ReadyForQueryMessage }
import de.envisia.postgresql.message.frontend.{ CredentialMessage, StartupMessage }
import de.envisia.postgresql.message.{ DelayedMessage, Message, QueryMessage }

import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success }

class PostgresClient(host: String, port: Int)(implicit actorSystem: ActorSystem, mat: Materializer) {

  private implicit val ec = actorSystem.dispatcher
  private type PromiseMessage = Option[Promise[PostgreServerMessage]]
  private var queue: SourceQueueWithComplete[OutMessage] = null

  def connect: Future[InitialPostgreConnection] = {
    val ready: Promise[Tcp.OutgoingConnection] = Promise()
    val sourceQueue = Source.queue[SourcedInMessage](100, OverflowStrategy.backpressure)
    val (source, publisher) = sourceQueue.toMat(Sink.asPublisher(false))(Keep.both).run()

    val out = Sink.foreach[OutMessage] {
      case challenge: AuthenticationChallengeMD5 =>
        source.offer((CredentialMessage("loki", "loki", challenge.challengeType, challenge.salt), None))
        println(s"[SINK]: MD5 Challenge: ${challenge.salt}")
      case msg => println(s"[SINK]: Other Message: $msg")
    }

    postgresClientFlow(Source.fromPublisher(publisher), out, ready)
    ready.future.map(_ => new InitialPostgreConnection(source)) //.flatMap(init(_, source))
  }

  private def stageLogic = new GraphStage[BidiShape[SourcedInMessage, InMessage, OutMessage, Message]] {
    val clientIn = Inlet[SourcedInMessage]("Client.in")
    val clientOut = Outlet[InMessage]("Client.out")
    val serverIn = Inlet[OutMessage]("Server.int")
    val serverOut = Outlet[Message]("Server.out")

    val shape: BidiShape[SourcedInMessage, InMessage, OutMessage, Message] =
      BidiShape(clientIn, clientOut, serverIn, serverOut)
    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      var state: Option[SourceQueueWithComplete[OutMessage]] = None
      var readyForQuery: Boolean = false
      var pickup: Message = null

      def passtroughMessages(msg: InMessage) = {
        msg match {
          case _: StartupMessage => true
          case _: CredentialMessage => true
          case _ => false
        }
      }

      // wip
      // every message until ReadyForQueryMessage(I) (especially ParameterStatusMessage)
      // needs to be saved and materialized as a initial ConnectionState
      // even AuthenticationOkMessage and others needs to be checked, so that we have a correct flow
      // we also need to check if the messages are sticky i.e. if a transaction is running we can't actually send
      // other queries down the road
      // postgresql sends a ReadyForQuery
      // and a command completion after each query

      // POC:
      // If the Logic isn't ready for Query we won't omit any messages to the connection and
      // ReQuery them which allows another backpressure cycle.
      // Actually the initials messages need to come trough
      // we also need a way to have a Source[T] to the client, so that the message's won't be stupid List[T]
      // However this can be hard since we need another stream that needs to backpressure somehow

      setHandler(clientIn, new InHandler {

        def onPush(): Unit = {
          val (msg, source) = grab(clientIn)

          println(s"ON PUSH CLIENT IN: $msg")

          if (readyForQuery || passtroughMessages(msg)) {
            if (state.isEmpty) {
              state = source
            }
            push(clientOut, msg)
          } else {
            if (isAvailable(serverOut)) {
              push(serverOut, DelayedMessage(msg, source))
            } else {
              pickup = DelayedMessage(msg, source)
            }
          }
        }

      })

      setHandler(clientOut, new OutHandler {

        def onPull(): Unit = {
          pull(clientIn)
        }

      })

      setHandler(serverIn, new InHandler {

        // Messages from the server will be coming in here
        def onPush(): Unit = {
          val elem = grab(serverIn)

          println(s"ON PUSH SERVER IN: $elem -- State: $state")

          state.foreach {
            _.offer(elem)
          }

          elem match {
            case ReadyForQueryMessage(_) =>
              // completes the state Source
              state.foreach(_.complete())
              state = None
              readyForQuery = true
            case _ => // this could be ignored safely
          }

          push(serverOut, QueryMessage(elem))
        }

      })

      setHandler(serverOut, new OutHandler {

        def onPull(): Unit = {
          if (pickup != null) {
            push(serverOut, pickup)
            pickup = null
          } else {
            pull(serverIn)
          }
        }

      })
    }
  }

  private def postgresClientFlow(source: Source[SourcedInMessage, NotUsed],
      sink: Sink[OutMessage, Any], ready: Promise[Tcp.OutgoingConnection]) = {
    val connection = Tcp().outgoingConnection(host, port).mapMaterializedValue(_.onComplete {
      case Success(outgoingConnection) => ready.trySuccess(outgoingConnection)
      case Failure(ex) => ready.failure(ex)
    }).join(new PostgreProtocol(StandardCharsets.UTF_8).serialization)

    def outMessageFilter(msg: Message): Boolean = {
      msg match {
        case _: QueryMessage => true
        case _ => false
      }
    }

    val unpackFlow = Flow[Message].map {
      case DelayedMessage(delayed, promise) => (delayed, promise)
      case _ => throw new IllegalStateException()
    }
    val unpackQuery: Flow[Message, PostgreServerMessage, NotUsed] = Flow[Message].map {
      case QueryMessage(msg) => msg
      case _ => throw new IllegalStateException()
    }
    val outMessageFlowFilter = Flow[Message].filter(!outMessageFilter(_))
    val outQueryFlowFIlter = Flow[Message].filter(outMessageFilter)
    val bufferFlow = Flow[SourcedInMessage].buffer(10, OverflowStrategy.backpressure)

    RunnableGraph.fromGraph(GraphDSL.create(source, sink)((_, _)) { implicit builder =>
      (sourceQ, sinkQ) =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[Message](2))
        val merge = builder.add(Merge[SourcedInMessage](2))

        sourceQ ~> merge ~> BidiFlow.fromGraph(stageLogic).join(connection) ~> broadcast ~> outQueryFlowFIlter ~> unpackQuery ~> sinkQ
        merge <~ bufferFlow <~ unpackFlow <~ outMessageFlowFilter <~ broadcast
        ClosedShape
    }).run()
  }

}
