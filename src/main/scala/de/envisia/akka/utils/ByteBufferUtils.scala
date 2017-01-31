/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.akka.utils

import java.nio.ByteBuffer
import java.nio.charset.Charset

object ByteBufferUtils {

  def slice(buf: ByteBuffer, length: Int): ByteBuffer = {
    val bytes = new Array[Byte](length)
    buf.get(bytes, 0, length)
    ByteBuffer.wrap(bytes)
  }

  def asArray(buf: ByteBuffer, length: Int): Array[Byte] = {
    val bytes = new Array[Byte](length)
    buf.get(bytes, 0, length)
    bytes
  }

  implicit class ExtendedByteBuffer(wrapped: ByteBuffer) {

    def asArray: Array[Byte] = {
      ByteBufferUtils.asArray(wrapped, wrapped.remaining())
    }

  }

  def readCString(buf: ByteBuffer, charset: Charset): String = {
    var byte: Byte = 0
    var count = 0

    buf.mark()

    do {
      byte = buf.get()
      count += 1
    } while (byte != 0)

    buf.reset()

    val result = new String(slice(buf, count - 1).array(), charset)

    // slices the first byte so that the buffer remove's the 0 byte
    val _ = slice(buf, 1)

    result
  }

}
