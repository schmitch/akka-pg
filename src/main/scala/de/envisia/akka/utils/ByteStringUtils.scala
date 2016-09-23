/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.akka.utils

import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.Charset

import akka.util.{ByteString, ByteStringBuilder}

object ByteStringUtils {

  def putCString(builder: ByteStringBuilder, data: String, charset: Charset): Unit = {
    builder.putBytes(data.getBytes(charset))
    builder.putByte(0)
  }

  def resultWithLength(byte: Byte, builder: ByteStringBuilder)(implicit byteOrder: ByteOrder): ByteString = {
    val finalBuilder = ByteString.newBuilder
    finalBuilder.putByte(byte).putInt(builder.length + 4).append(builder.result()).result()
  }

  def resultWithLength(builder: ByteStringBuilder)(implicit byteOrder: ByteOrder): ByteString = {
    val finalBuilder = ByteString.newBuilder
    finalBuilder.putInt(builder.length + 4).append(builder.result()).result()
  }

  def fromBuffer(buf: ByteBuffer, length: Int): ByteString = {
    val bytes = new Array[Byte](length)
    buf.get(bytes, 0, length)
    ByteString.fromArray(bytes)
  }

}
