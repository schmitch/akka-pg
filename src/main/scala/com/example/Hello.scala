package com.example

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import de.envisia.postgresql.impl.engine.PostgresClient

import scala.io.StdIn

object Hello {

  def main(args: Array[String]): Unit = {
    implicit val actorSystem = ActorSystem()
    implicit val mat = ActorMaterializer()
    implicit val executionContext = actorSystem.dispatcher

    val client = new PostgresClient("172.16.206.100", 5432, "loki", Some("loki"), Some("loki"))

    client.newSource().runWith(Sink.foreach { v =>
      println(s"V: $v")
    })

    client.newSource().runWith(Sink.foreach { v =>
      println(s"Q: $v")
    })

    client.executeQuery("LISTEN envisia;")

    StdIn.readLine()
  }

}
