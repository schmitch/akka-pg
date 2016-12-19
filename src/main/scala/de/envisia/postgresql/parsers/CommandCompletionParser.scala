/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.ByteBuffer
import java.nio.charset.Charset

import de.envisia.akka.utils.ByteBufferUtils
import de.envisia.postgresql.message.backend.{ CommandCompleteMessage, ServerMessage }

import scala.util.control.Exception.allCatch

class CommandCompletionParser(charset: Charset) extends MessageParser {

  override def parseMessage(buf: ByteBuffer): ServerMessage = {
    val result = ByteBufferUtils.readCString(buf, charset)
    val indexOfRowCount = result.lastIndexOf(" ")
    val rowCount = if (indexOfRowCount == -1) 0 else allCatch.opt(result.substring(indexOfRowCount).trim.toInt).getOrElse(0)
    CommandCompleteMessage(rowCount, result)
  }

}
