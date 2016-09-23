package com.example

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import de.envisia.postgresql.impl.engine2.PostgresClient
import de.envisia.postgresql.message.frontend.StartupMessage

import scala.util.{ Failure, Success }

//import akka.io.{ IO, Tcp => ActorTcp }
//import akka.stream.scaladsl.{ RunnableGraph, Tcp }
//import com.example.actors.{ PostgresClient, PostgresConnection }
//import de.envisia.postgresql.impl.engine.PostgresConnection

//import scala.concurrent.duration._
//import scala.concurrent.{ Await, Future }
//import scala.io.StdIn

object Hello {
  //  def runStuff(connection: RunnableGraph[Future[Tcp.OutgoingConnection]])(implicit mat: Materializer) = {
  //    connection.run()
  //  }

  def main(args: Array[String]): Unit = {
    implicit val actorSystem = ActorSystem()
    implicit val mat = ActorMaterializer()
    implicit val executionContext = actorSystem.dispatcher

    new PostgresClient("localhost", 5432).connect.onComplete {
      case Success(connection) =>
        val msg = StartupMessage(List(
          "database" -> "loki",
          "user" -> "loki",
          "client_encoding" -> "UTF8",
          "DateStyle" -> "ISO",
          "extra_float_digits" -> "2",
          "application_name" -> "envisia-akka-client"
        ))

        connection.send(msg).to(Sink.foreach{ data =>
          println(s"RECV: $data")
        })

      case Failure(f) => println(f)

    }

  }

}
