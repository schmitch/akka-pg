/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.encoders

import java.nio.ByteOrder
import java.nio.charset.Charset

import akka.util.ByteString
import de.envisia.akka.utils.ByteStringUtils
import de.envisia.postgresql.message.backend.{ AuthenticationResponseType, ServerMessage }
import de.envisia.postgresql.message.frontend.CredentialMessage
import de.envisia.postgresql.util.PasswordHelper

class CredentialEncoder(charset: Charset) {

  // To build a correct message we need to use BIG_ENDIAN
  private implicit val order: ByteOrder = ByteOrder.BIG_ENDIAN

  def encode(message: CredentialMessage): ByteString = {
    val password = message.authenticationType match {
      case AuthenticationResponseType.Cleartext =>
        message.password.getBytes(charset)
      case AuthenticationResponseType.MD5 =>
        PasswordHelper.encode(message.username, message.password, message.salt.get, charset)
    }

    val builder = ByteString.newBuilder
    builder.putBytes(password)
    builder.putByte(0)

    ByteStringUtils.resultWithLength(ServerMessage.PasswordMessage, builder)
  }

}