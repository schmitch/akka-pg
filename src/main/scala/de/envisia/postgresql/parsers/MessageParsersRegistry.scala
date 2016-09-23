/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.ByteBuffer
import java.nio.charset.Charset

import de.envisia.postgresql.message.backend.ServerMessage

class MessageParsersRegistry(charset: Charset) {

  private val parameterStatusParser = new ParameterStatusParser(charset)

  def parseFor(code: Int, msg: ByteBuffer): ServerMessage = {
    val parser = code match {
      case ServerMessage.Authentication => AuthenticationStartupParser
      case ServerMessage.BackendKeyData => BackendKeyDataParser
      case ServerMessage.BindComplete => ReturningMessageParser.BindCompleteMessageParser
      case ServerMessage.CloseComplete => ReturningMessageParser.CloseCompleteMessageParser
      case ServerMessage.EmptyQueryString => ReturningMessageParser.EmptyQueryStringMessageParser
      case ServerMessage.NoData => ReturningMessageParser.NoDataMessageParser
      case ServerMessage.ParseComplete => ReturningMessageParser.ParseCompleteMessageParser
      case ServerMessage.ParameterStatus => parameterStatusParser
      case ServerMessage.ReadyForQuery => ReadyForQueryParser

      case _ => throw new Exception("ParserNotAvailableException")
    }

    parser.parseMessage(msg)
  }

}
