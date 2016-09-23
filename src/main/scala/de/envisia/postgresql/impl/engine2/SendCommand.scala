/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine2

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Keep, Sink, Source, SourceQueueWithComplete }
import akka.stream.{ Materializer, OverflowStrategy }

private[engine2] class InitialPostgreConnection(sendQueue: SourceQueueWithComplete[SourcedInMessage])
    (implicit actorSystem: ActorSystem, mat: Materializer) {

  def send(data: InMessage): Source[OutMessage, NotUsed] = {
    val (queue, publisher) = Source.queue[OutMessage](100, OverflowStrategy.backpressure).toMat(Sink.asPublisher(false))(Keep.both).run()

    sendQueue.offer((data, Some(queue)))

    Source.fromPublisher(publisher)
  }

}

private[engine2] class PostgreConnection(sendQueue: SourceQueueWithComplete[SourcedInMessage],
    val props: Map[String, String])(implicit actorSystem: ActorSystem, mat: Materializer)
    extends InitialPostgreConnection(sendQueue) {

  lazy val applicationName: String = props("application_name")


}

//  def disconnect(): Future[Unit] = {
//    val promise = Promise[ByteString]()
//    //sendQueue.offer(SendCommand(ByteString.fromString("QUIT"), promise))
//    promise.future.map(_ => sendQueue.complete())
//  }
//override def sendQueue: SourceQueueWithComplete[SendCommand] = ???