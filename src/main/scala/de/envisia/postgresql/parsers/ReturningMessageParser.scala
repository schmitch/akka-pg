/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.ByteBuffer

import de.envisia.postgresql.message.backend._

object ReturningMessageParser {

  val BindCompleteMessageParser: MessageParser = new ReturningMessageParser(BindComplete)
  val CloseCompleteMessageParser: MessageParser = new ReturningMessageParser(CloseComplete)
  val EmptyQueryStringMessageParser: MessageParser = new ReturningMessageParser(EmptyQueryString)
  val NoDataMessageParser: MessageParser = new ReturningMessageParser(NoData)
  val ParseCompleteMessageParser: MessageParser = new ReturningMessageParser(ParseComplete)

}

class ReturningMessageParser(message: ServerMessage) extends MessageParser {

  def parseMessage(buffer: ByteBuffer): ServerMessage = message

}
