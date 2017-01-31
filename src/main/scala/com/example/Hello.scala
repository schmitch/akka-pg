package com.example

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import de.envisia.postgresql.impl.engine.PostgresClient

import scala.concurrent.{ ExecutionContext, Future }
import scala.io.StdIn
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

object Hello {

  private def exec(client: PostgresClient)(implicit ex: ExecutionContext): Future[Any] = {
    client.executeQuery("SELECT 1;") /*.flatMap { _ => exec(client) }.recoverWith {
      case NonFatal(f) => println(s"FATALE: $f");exec(client)
    }*/
  }

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

    client.executeQuery("LISTEN envisia;").onComplete{
      case Success(_) => println("Listen to envisia")
      case Failure(t) => println(s"Failed to Listen to envisia $t")
    }

    StdIn.readLine()
  }

}
