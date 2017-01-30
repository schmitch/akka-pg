/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.ByteBuffer
import java.nio.charset.Charset

import de.envisia.akka.utils.ByteBufferUtils
import de.envisia.postgresql.message.backend.ServerMessage

abstract class InformationParser(charset: Charset) extends MessageParser {

  override def parseMessage(b: ByteBuffer): ServerMessage = {
    val fields = scala.collection.mutable.Map[Char, String]()
    while (b.hasRemaining) {

      val kind = b.get()

      if (kind != 0) {
        fields.put(kind.toChar, ByteBufferUtils.readCString(b, charset))
      }
    }

    createMessage(fields.toMap)
  }

  def createMessage(fields: Map[Char, String]): ServerMessage

}