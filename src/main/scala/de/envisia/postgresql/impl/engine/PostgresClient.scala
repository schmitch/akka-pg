/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source, SourceQueueWithComplete}
import de.envisia.postgresql.message.backend.PostgreServerMessage
import de.envisia.postgresql.message.frontend.QueryMessage

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

class PostgresClient(host: String, port: Int, database: String, username: Option[String],
    password: Option[String], timeout: FiniteDuration = 5.seconds)(implicit actorSystem: ActorSystem, mat: Materializer) {

  private implicit val ec = mat.executionContext

  private final val bufferSize = 128

  private val decider: Supervision.Decider = { t =>
    println(s"OUTER: $t")
    Supervision.Resume
  }

  private def connectionFlow: Flow[PostgreClientMessage, PostgreServerMessage, () => Future[ConnectionState]] = {
    Flow[PostgreClientMessage].viaMat(new ConnectionManagement(EngineVar(host, port, database, username, password, timeout), bufferSize))(Keep.right)
  }

  private val ((sink, getState), source) = MergeHub.source[PostgreClientMessage](perProducerBufferSize = bufferSize)
      .viaMat(connectionFlow)(Keep.both)
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
      .toMat(BroadcastHub.sink[PostgreServerMessage](bufferSize = bufferSize))(Keep.both)
      .run()

  // Default Source
  private val queue: SourceQueueWithComplete[PostgreClientMessage] = Source
      .queue[PostgreClientMessage](bufferSize, OverflowStrategy.fail)
      .toMat(sink)(Keep.left)
      .run()

  // Default Sink
  source.runWith(Sink.ignore)

  def newSource(buffer: Int = bufferSize, timeout: FiniteDuration = 5.seconds): Source[PostgreServerMessage, NotUsed] = {
    source.backpressureTimeout(timeout).buffer(buffer, OverflowStrategy.fail)
  }

  def executeQuery(query: String): Future[Any] = {
    queue.offer(QueryMessage(query)).map {
      case QueueOfferResult.Enqueued => println("Enqueued")
      case QueueOfferResult.Dropped => println("Dropped")
      case QueueOfferResult.QueueClosed => throw new Exception("closed")
      case QueueOfferResult.Failure(t) => println(s"Failure: $t")
    }
  }

  def checkState: Future[ConnectionState] = {
    getState()
  }

}
