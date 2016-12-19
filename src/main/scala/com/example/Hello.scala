package com.example

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import de.envisia.postgresql.impl.engine3.{ PostgreClientMessage, PostgresClient }
import de.envisia.postgresql.message.frontend.QueryMessage

import scala.io.StdIn

object Hello {

  def main(args: Array[String]): Unit = {
    implicit val actorSystem = ActorSystem()
    implicit val mat = ActorMaterializer()
    implicit val executionContext = actorSystem.dispatcher

    val client = new PostgresClient("localhost", 5432, "loki", Some("loki"), Some("loki"))

    client.queue.offer(new QueryMessage("LISTEN envisia;"): PostgreClientMessage)
    client.queue.offer(new QueryMessage("LISTEN envisia;"): PostgreClientMessage)
    client.queue.offer(new QueryMessage("LISTEN envisia;"): PostgreClientMessage)
    client.queue.offer(new QueryMessage("LISTEN envisia;"): PostgreClientMessage)
    client.queue.offer(new QueryMessage("LISTEN envisia;"): PostgreClientMessage)

    StdIn.readLine()
  }

}
