/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.OutgoingConnection
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Sink, Source, SourceQueueWithComplete, Tcp, Zip }
import akka.stream.{ FlowShape, Materializer, OverflowStrategy }
import akka.util.ByteString
import de.envisia.akka.utils.ByteBufferUtils
import de.envisia.postgresql.encoders.{ CredentialEncoder, StartupMessageEncoder }
import de.envisia.postgresql.message.backend._
import de.envisia.postgresql.message.frontend.StartupMessage
import de.envisia.postgresql.parsers.{ AuthenticationStartupParser, MessageParsersRegistry }

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success }

class PostgreClient(host: String, port: Int, database: String, username: Option[String] = None,
    password: Option[String] = None)(implicit actorSystem: ActorSystem, mat: Materializer) {

  private implicit val ec = actorSystem.dispatcher

  private val charset = StandardCharsets.UTF_8
  private val startupMessageEncoder = new StartupMessageEncoder(charset)
  private val credentialEncoder = new CredentialEncoder(charset)
  private val messageRegistry = new MessageParsersRegistry(charset)

  private val frameFlow = Flow[ByteString].map { cmd =>
    // val builder = ByteString.newBuilder
    // builder.append(cmd).putBytes(Commands.LS).result()

    cmd
  }

  def connect: Future[PostgreConnection] = {
    val ready: Promise[Tcp.OutgoingConnection] = Promise()
    val sourceQueue = Source.queue[SendCommand](100, OverflowStrategy.backpressure)
    val connection = Tcp().outgoingConnection(host, port)
    val out = Sink.foreach[(ByteString, Promise[ByteString])] { case (data, promise) =>
      val strData = data.utf8String
      if (strData.startsWith("-")) promise.tryFailure(new Exception(strData))
      else promise.trySuccess(data)
    }

    val source = sourceQueue.via(sendFlow(connection, ready)).to(out).run()

    ready.future.map(_ => new InitialPostgreConnection(source)).flatMap(init(_, source))
  }

  private def sendFlow(connection: Flow[ByteString, ByteString, Future[OutgoingConnection]],
      ready: Promise[Tcp.OutgoingConnection]) = GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val bcast = b.add(Broadcast[SendCommand](2))
    val zipper = b.add(Zip[ByteString, Promise[ByteString]]())

    bcast.out(0) ~> Flow[SendCommand].map(_.msg).via(frameFlow) ~> connection.mapMaterializedValue(_.onComplete {
      case Success(outgoingConnection) => ready.trySuccess(outgoingConnection)
      case Failure(ex) => ready.failure(ex)
    }) ~> zipper.in0
    bcast.out(1) ~> Flow[SendCommand].map(_.promise) ~> zipper.in1

    FlowShape(bcast.in, zipper.out)
  }

  private def init(connection: InitialPostgreConnection,
      sendQueue: SourceQueueWithComplete[SendCommand]): Future[PostgreConnection] = {

    connection.send(startupMessageEncoder.encode(StartupMessage(List(
      "database" -> database,
      "user" -> username.orNull,
      "user" -> password.orNull,
      "client_encoding" -> "UTF8",
      "DateStyle" -> "ISO",
      "extra_float_digits" -> "2",
      "application_name" -> "envisia-akka-client"
    )))).flatMap { data =>
      val decoded = decode(data)

      var authenticationMessage: AuthenticationMessage = null
      var readyMessage: Option[ReadyForQueryMessage] = None
      val props = mutable.ListBuffer[ParameterStatusMessage]()
      decoded.foreach {
        case am: AuthenticationMessage => authenticationMessage = am
        case param: ParameterStatusMessage => props.append(param)
        case ready: ReadyForQueryMessage => readyMessage = Some(ready)
        case everythingElse => println(s"Not a useful Message: $everythingElse")
      }

      authenticationMessage match {
        case AuthenticationOkMessage =>
          readyMessage match {
            case Some(ready) => Future.successful(new PostgreConnection(sendQueue,
              props.map(data => data.key -> data.value)(collection.breakOut): Map[String, String]))
            case None => Future.failed(new Exception("not ready for queries"))
          }
        case msg => Future.failed(new Exception(s"Unsupported Authentication Message: $msg"))
      }
    }
  }

  private def decode(data: ByteString): List[ServerMessage] = {
    @tailrec
    def next(buf: ByteBuffer, messages: List[ServerMessage] = Nil): List[ServerMessage] = {
      if (buf.hasRemaining) {
        val code = buf.get()
        val lengthWithSelf = buf.getInt
        val length = lengthWithSelf - 4
        if (length < 0) {
          throw new Exception("negative message size exception")
        }
        if (buf.remaining() >= length) {
          val data = ByteBufferUtils.slice(buf, length)
          val result = code match {
            case ServerMessage.Authentication => AuthenticationStartupParser.parseMessage(data)
            case sm => messageRegistry.parseFor(code, data)
          }
          next(buf, result :: messages)
        } else {
          throw new Exception("buffer not big enough to read messages")
        }
      } else {
        messages.reverse
      }
    }

    next(data.toByteBuffer)
  }

}
