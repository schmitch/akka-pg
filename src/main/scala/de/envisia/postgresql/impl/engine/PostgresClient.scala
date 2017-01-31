/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ BroadcastHub, Flow, Keep, Sink, Source, Tcp }
import de.envisia.postgresql.codec._
import de.envisia.postgresql.message.backend.NotificationResponse
import de.envisia.postgresql.message.frontend.QueryMessage

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }

class PostgresClient(
    host: String,
    port: Int,
    database: String,
    username: Option[String],
    password: Option[String],
    timeout: FiniteDuration = 5.seconds
)(implicit actorSystem: ActorSystem, mat: Materializer) {
  private implicit val ec = mat.executionContext
  private final val bufferSize = 128

  private val decider: Supervision.Decider = { t =>
    Supervision.Resume
  }

  private def connectionFlow: Flow[InMessage, OutMessage, () => Future[Tcp.OutgoingConnection]] = {
    Flow[InMessage]
      .viaMat(new ConnectionManagement(EngineVar(host, port, database, username, password, timeout), bufferSize * 2))(Keep.right)
  }

  private val ((queue, getState), source) = Source.queue[InMessage](bufferSize, OverflowStrategy.dropNew)
    .viaMat(connectionFlow)(Keep.both)
    .withAttributes(ActorAttributes.supervisionStrategy(decider))
    .toMat(BroadcastHub.sink[OutMessage](bufferSize = bufferSize))(Keep.both)
    .run()

  // Default Sink
  source.runWith(Sink.ignore)

  def newSource(buffer: Int = bufferSize, timeout: FiniteDuration = 5.seconds): Source[OutMessage, UniqueKillSwitch] = {
    // Notifications should only be allowed to a single Backend
    source.viaMat(KillSwitches.single)(Keep.right).filter {
      case SimpleMessage(pgs) => pgs match {
        case _: NotificationResponse => true
        case _ => false
      }
      case _ => false
    }.backpressureTimeout(timeout).buffer(buffer * 2, OverflowStrategy.dropNew)
  }

  def executeQuery(query: String): Future[Message] = {
    val p = Promise[Message]()
    // FIXME: check the offer responses
    queue.offer(ReturnDispatch(QueryMessage(query), p))
    p.future
  }

  def checkState: Future[Tcp.OutgoingConnection] = {
    getState()
  }

}
