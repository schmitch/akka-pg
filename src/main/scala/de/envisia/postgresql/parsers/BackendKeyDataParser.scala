/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.ByteBuffer

import de.envisia.postgresql.message.backend.{ ProcessData, ServerMessage }

object BackendKeyDataParser extends MessageParser {

  override def parseMessage(buf: ByteBuffer): ServerMessage = {
    ProcessData(buf.getInt, buf.getInt)
  }

}
