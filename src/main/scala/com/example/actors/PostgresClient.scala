/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package com.example.actors

import java.nio.charset.StandardCharsets

import akka.actor.{ Actor, ActorLogging, Props }
import akka.util.ByteString
import de.envisia.akka.utils.ByteBufferUtils
import de.envisia.postgresql.encoders.StartupMessageEncoder
import de.envisia.postgresql.message.backend.ServerMessage
import de.envisia.postgresql.message.frontend.StartupMessage
import de.envisia.postgresql.parsers.{ AuthenticationStartupParser, MessageParsersRegistry }

object PostgresClient {
  def props(properties: List[(String, Any)]) = Props(classOf[PostgresClient], properties: List[(String, Any)])
}

class PostgresClient(properties: List[(String, Any)]) extends Actor with ActorLogging {

  import akka.io.Tcp._

  val charset = StandardCharsets.UTF_8
  val startupMessageEncoder = new StartupMessageEncoder(charset)
  val messageRegistry = new MessageParsersRegistry(charset)

  override def receive: Receive = {
    case c@Connected(remoteAddress, local) =>
      log.info(s"Connected")
      sender() ! startupMessageEncoder.encode(StartupMessage(properties))

    // fixme:https://github.com/mauricio/postgresql-async/blob/c94b0ad89e5a72df6c5a21e71c48d9e814e8686d/postgresql-async/src/main/scala/com/github/mauricio/async/db/postgresql/codec/MessageDecoder.scala
    // Decodes the incoming message
    case data: ByteString =>
      val buf = data.toByteBuffer

      while (buf.hasRemaining) {
        val code = buf.get()
        val lengthWithSelf = buf.getInt
        val length = lengthWithSelf - 4
        log.info(s"Message Length: $lengthWithSelf")
        if (length < 0) {
          throw new Exception("negative message size exception")
        }
        if (buf.remaining() >= length) {
          log.debug(s"Received buffer ${code}\n")

          val data = ByteBufferUtils.slice(buf, length)
          val result = code match {
            case ServerMessage.Authentication => AuthenticationStartupParser.parseMessage(data)
            case sm => messageRegistry.parseFor(code, data)
          }
          log.info(s"Result: $result")
        }

      }

    case s: String => log.info(s"Message: $s")
  }
}
