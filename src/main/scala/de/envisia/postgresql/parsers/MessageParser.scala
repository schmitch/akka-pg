/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.ByteBuffer

import de.envisia.postgresql.message.backend.ServerMessage

trait MessageParser {

  def parseMessage(msg: ByteBuffer): ServerMessage

}
