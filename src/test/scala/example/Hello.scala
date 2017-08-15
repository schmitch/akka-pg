package example

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.stream.{ ActorMaterializer, Materializer }
import de.envisia.postgresql.impl.engine.PostgresClient

import scala.concurrent.{ ExecutionContext, Future }
import scala.io.StdIn
import scala.util.{ Failure, Success }

object Hello {

  private def exec(client: PostgresClient)(implicit ex: ExecutionContext): Future[Any] = {
    client.executeQuery("SELECT 1;")
  }

  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem()
    implicit val mat: Materializer = ActorMaterializer()
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher

    val client = new PostgresClient("localhost", 5432, "loki", Some("loki"), Some("loki"))

    println("Client Started")

    client.newSource().runWith(Sink.foreach { v =>
      println(s"V: $v")
    })

    client.newSource().runWith(Sink.foreach { v =>
      println(s"Q: $v")
    })

    client.listen("envisia").onComplete {
      case Success(_) => println("Listen to envisia")
      case Failure(t) => println(s"Failed to Listen to envisia $t")
    }

    StdIn.readLine()
  }

}
