/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.ByteBuffer

import de.envisia.postgresql.message.backend._

object ReturningMessageParser {

  val BindCompleteMessageParser = new ReturningMessageParser(BindComplete)
  val CloseCompleteMessageParser = new ReturningMessageParser(CloseComplete)
  val EmptyQueryStringMessageParser = new ReturningMessageParser(EmptyQueryString)
  val NoDataMessageParser = new ReturningMessageParser(NoData)
  val ParseCompleteMessageParser = new ReturningMessageParser(ParseComplete)

}

class ReturningMessageParser(message: ServerMessage) extends MessageParser {

  def parseMessage(buffer: ByteBuffer): ServerMessage = message

}
