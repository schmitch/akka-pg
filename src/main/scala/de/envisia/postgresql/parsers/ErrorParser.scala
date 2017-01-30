/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.charset.Charset

import de.envisia.postgresql.message.backend.{ErrorMessage, ServerMessage}

class ErrorParser(charset: Charset) extends InformationParser(charset) {

  override def createMessage(fields: Map[Char, String]): ServerMessage = {
    ErrorMessage(fields)
  }

}
