/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.encoders

import java.nio.ByteOrder
import java.nio.charset.Charset

import akka.util.ByteString
import de.envisia.akka.utils.ByteStringUtils
import de.envisia.postgresql.message.backend.ServerMessage
import de.envisia.postgresql.message.frontend.{ ClientMessage, QueryMessage }

class QueryMessageEncoder(charset: Charset) {

  // To build a correct message we need to use BIG_ENDIAN
  private implicit val order: ByteOrder = ByteOrder.BIG_ENDIAN

  def encode(message: ClientMessage): ByteString = {
    val builder = ByteString.newBuilder
    ByteStringUtils.putCString(builder, message.asInstanceOf[QueryMessage].query, charset)

    ByteStringUtils.resultWithLength(ServerMessage.Query, builder)
  }

}
