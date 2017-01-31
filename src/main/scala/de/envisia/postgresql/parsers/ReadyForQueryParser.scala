/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.ByteBuffer

import de.envisia.postgresql.message.backend.{ ReadyForQueryMessage, ServerMessage }

object ReadyForQueryParser extends MessageParser {

  override def parseMessage(buf: ByteBuffer): ServerMessage = {
    ReadyForQueryMessage(buf.get().toChar)
  }

}
