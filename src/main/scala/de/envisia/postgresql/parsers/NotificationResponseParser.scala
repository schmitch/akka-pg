/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.ByteBuffer
import java.nio.charset.Charset

import de.envisia.akka.utils.ByteBufferUtils
import de.envisia.postgresql.message.backend.{ NotificationResponse, ServerMessage }

class NotificationResponseParser(charset: Charset) extends MessageParser {

  override def parseMessage(buf: ByteBuffer): ServerMessage = {
   NotificationResponse(
      buf.getInt,
      ByteBufferUtils.readCString(buf, charset),
      ByteBufferUtils.readCString(buf, charset)
    )
  }

}
