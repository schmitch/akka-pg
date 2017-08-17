package example

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.stream.{ ActorMaterializer, Materializer }
import de.envisia.postgresql.impl.engine.PostgresConnection

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext }
import scala.io.StdIn
import scala.util.control.NonFatal

object Hello {

  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem()
    implicit val mat: Materializer = ActorMaterializer()
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher

    val client = PostgresConnection("localhost", 5432, "loki", Some("loki"), Some("loki"))
    sys.addShutdownHook(Await.ready(client.shutdown(), 10.minutes))

    println("Client Started")

    Await.ready(client.executeQuery("SELECT * FROM crm_customer;").flatMap(_.runWith(Sink.seq)).map { first =>
      println(s"First Message: $first")

    }.recover {
      case NonFatal(t) => println(s"Fail: $t")
    }, 10.minutes)

    println("p2")

    StdIn.readLine()
  }

}
