/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import java.nio.ByteBuffer
import java.nio.charset.Charset

import akka.NotUsed
import akka.stream.javadsl.BidiFlow
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import de.envisia.akka.utils.ByteBufferUtils
import de.envisia.postgresql.encoders.{ CredentialEncoder, StartupMessageEncoder }
import de.envisia.postgresql.message.backend.{ PostgreServerMessage, ServerMessage }
import de.envisia.postgresql.message.frontend.{ CredentialMessage, StartupMessage }
import de.envisia.postgresql.parsers.{ AuthenticationStartupParser, MessageParsersRegistry }

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

class PostgreProtocol(charset: Charset) {

  private val zero = ByteString.newBuilder.putByte(0).result()

  private val startupMessageEncoder = new StartupMessageEncoder(charset)
  private val credentialEncoder = new CredentialEncoder(charset)
  private val messageRegistry = new MessageParsersRegistry(charset)

  def serialization: BidiFlow[ByteString, PostgreServerMessage, PostgreClientMessage, ByteString, NotUsed] = {
    val readFlow = Flow[ByteString]
        // convert ByteString to PostgreMessage
        .map(read(_, charset))
        // pass on successfully parsed PostgreMessage and strip out unparseble ones
        .mapConcat {
          case Success(cmd) => cmd :: Nil
          case Failure(cause) => println(s"FAILURE: $cause"); Nil
        }
        // How to make that work?!
        .mapConcat(identity)


    val writeFlow = Flow[PostgreClientMessage]
        // convert PostgreClientMessage to ByteString (this will add necessary Zero Bytes)
        .map(write(_, charset))

    BidiFlow.fromFlows(readFlow, writeFlow)
  }

  case class GroupedServerMessage(data: List[ServerMessage]) extends PostgreServerMessage

  def read(bs: ByteString, charset: Charset): Try[List[ServerMessage]] = {
    // fixme: create a correct decoder
    try {
      val messages = decode(bs)
      Success(messages)
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def write(msg: PostgreClientMessage, charset: Charset): ByteString = {
    msg match {
      case sm: StartupMessage => startupMessageEncoder.encode(sm)
      case cred: CredentialMessage => credentialEncoder.encode(cred)
      case _ => println(s"EMPTY MSG"); ByteString.fromString("")
    }
  }

  private def decode(data: ByteString): List[ServerMessage] = {
    @tailrec
    def next(buf: ByteBuffer, messages: List[ServerMessage] = Nil): List[ServerMessage] = {
      if (buf.hasRemaining) {
        val code = buf.get()
        val lengthWithSelf = buf.getInt
        val length = lengthWithSelf - 4
        if (length < 0) {
          throw new Exception("negative message size exception")
        }
        if (buf.remaining() >= length) {
          val data = ByteBufferUtils.slice(buf, length)
          val result = code match {
            case ServerMessage.Authentication => AuthenticationStartupParser.parseMessage(data)
            case sm => messageRegistry.parseFor(code, data)
          }
          next(buf, result :: messages)
        } else {
          throw new Exception("buffer not big enough to read messages")
        }
      } else {
        messages.reverse
      }
    }

    next(data.toByteBuffer)
  }

}

trait PostgreClientMessage
