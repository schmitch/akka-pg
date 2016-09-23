/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import akka.actor.ActorSystem
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.util.ByteString

import scala.concurrent.{ Future, Promise }

case class SendCommand(msg: ByteString, promise: Promise[ByteString])

private[engine] class InitialPostgreConnection(sendQueue: SourceQueueWithComplete[SendCommand])
    (implicit actorSystem: ActorSystem) {

  private[engine] def send(data: ByteString): Future[ByteString] = {
    val promise = Promise[ByteString]()
    sendQueue.offer(SendCommand(data, promise))
    promise.future
  }

}

private[engine] class PostgreConnection(sendQueue: SourceQueueWithComplete[SendCommand],
    val props: Map[String, String])(implicit actorSystem: ActorSystem)
    extends InitialPostgreConnection(sendQueue) {

  lazy val applicationName: String = props("application_name")


}

//  def disconnect(): Future[Unit] = {
//    val promise = Promise[ByteString]()
//    //sendQueue.offer(SendCommand(ByteString.fromString("QUIT"), promise))
//    promise.future.map(_ => sendQueue.complete())
//  }
//override def sendQueue: SourceQueueWithComplete[SendCommand] = ???