/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.encoders

import java.nio.ByteOrder
import java.nio.charset.Charset

import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import de.envisia.akka.utils.ByteStringUtils
import de.envisia.postgresql.message.frontend.StartupMessage

class StartupMessageEncoder(charset: Charset) {

  // To build a correct message we need to use BIG_ENDIAN
  implicit val order = ByteOrder.BIG_ENDIAN

  def encode(startup: StartupMessage): ByteString = {
    val builder = ByteString.newBuilder
    builder.putShort(3)
    builder.putShort(0)

    startup.parameters.foreach { case (key, msg) =>
      msg match {
        case value: String =>
          ByteStringUtils.putCString(builder, key, charset)
          ByteStringUtils.putCString(builder, value, charset)
        case Some(value) =>
          ByteStringUtils.putCString(builder, key, charset)
          ByteStringUtils.putCString(builder, value.toString, charset)
        case _ =>
      }
    }

    builder.putByte(0)
    ByteStringUtils.resultWithLength(builder)
  }

}
