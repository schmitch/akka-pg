/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package com.example.actors

import java.net.InetSocketAddress

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.util.ByteString

object PostgresConnection {
  def props(remote: InetSocketAddress, manager: ActorRef, replies: ActorRef) =
    Props(classOf[PostgresConnection], remote, manager, replies)

}

class PostgresConnection(remote: InetSocketAddress, manager: ActorRef, listener: ActorRef)
    extends Actor with ActorLogging {

  import akka.io.Tcp._

  manager ! Connect(remote)

  override def receive: Receive = {
    case CommandFailed(_: Connect) =>
      log.info("Connection failed")
      listener ! "connect failed"
      context stop self

    case c@Connected(remoteAddress, local) =>
      listener ! c
      val connection = sender()
      connection ! Register(self)
      context become {
        case data: ByteString =>
          log.info(s"Data to write: ${data.utf8String}")
          connection ! Write(data)
        case CommandFailed(w: Write) =>
          // O/S buffer was full
          log.error("write failed")
          listener ! "write failed"
        case Received(data) =>
          log.info(s"Data received: ${data.utf8String}")
          listener ! data
        case "close" =>
          log.info("close")
          connection ! Close
        case _: ConnectionClosed =>
          listener ! "connection closed"
          log.info("connection closed")
          context stop self
        case any => log.info(s"ANY MSG: $any")
      }
  }

}
