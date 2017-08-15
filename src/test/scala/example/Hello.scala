package example

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import de.envisia.postgresql.impl.engine.PostgresConnection

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.io.StdIn

object Hello {

  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem()
    implicit val mat: Materializer = ActorMaterializer()
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher

    val client = PostgresConnection("localhost", 5432, "loki", Some("loki"), Some("loki"))
    sys.addShutdownHook(Await.ready(client.shutdown(), 10.minutes))

    println("Client Started")

    client.executeQuery("SELECT COUNT(*) FROM crm_customer;")
    client.executeQuery("SELECT * FROM crm_customer;")

    for (i <- 1 to 1000000) {
      client.executeQuery("SELECT COUNT(*) FROM crm_customer;")
      Thread.sleep(5000)
    }

    StdIn.readLine()
  }

}
