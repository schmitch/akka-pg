/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.ByteBuffer

import akka.util.ByteString
import de.envisia.postgresql.message.backend.{ DataRowMessage, ServerMessage }

object DataRowParser extends MessageParser {

  def parseMessage(buffer: ByteBuffer): ServerMessage = {

    val row = new Array[ByteString](buffer.getShort())
    row.indices.foreach { column =>
      val length = buffer.getInt()

      if (length != -1) {
        val data = new Array[Byte](length)
        buffer.get(data)
        row(column) = ByteString(data)
      } else {
        row(column) = null
      }
    }

    DataRowMessage(row)
  }

}