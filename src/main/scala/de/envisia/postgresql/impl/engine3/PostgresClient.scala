/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine3

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ BroadcastHub, Keep, MergeHub, Sink, Source, SourceQueueWithComplete, Tcp }
import de.envisia.postgresql.message.backend.PostgreServerMessage

class PostgresClient(host: String, port: Int, database: String, username: Option[String],
    password: Option[String])(implicit actorSystem: ActorSystem, mat: Materializer) {

  private val decider: Supervision.Decider = _ => Supervision.Restart

  private def connectionFlow = {
    // TODO: Built Authentication flow i.e. startup / password around
    Fusing.aggressive(Tcp().outgoingConnection(host, port)
        .join(new PostgreProtocol(StandardCharsets.UTF_8).serialization)
        .join(new PostgreStage(database, username, password)))
        .withAttributes(ActorAttributes.supervisionStrategy(decider))
  }

  private val (sink, source) = MergeHub.source[PostgreClientMessage](perProducerBufferSize = 128)
      .via(connectionFlow)
      .toMat(BroadcastHub.sink[PostgreServerMessage](bufferSize = 128))(Keep.both)
      .run()

  val queue: SourceQueueWithComplete[PostgreClientMessage] = Source
      .queue[PostgreClientMessage](128, OverflowStrategy.backpressure)
      .toMat(sink)(Keep.left)
      .run()

  // Runs the source with a default consumer
  private var c: Int = 0
  source.runWith(Sink.foreach { message =>
    c += 1
    println(s"Message: $message of $c")
  })

}
