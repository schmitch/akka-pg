/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.backend

import de.envisia.postgresql.message.backend.AuthenticationResponseType.AuthenticationResponseType

trait AuthenticationMessage extends ServerMessage {
  val kind: Int = ServerMessage.Authentication
}

case object AuthenticationOkMessage extends AuthenticationMessage

object AuthenticationResponseType extends Enumeration {
  type AuthenticationResponseType = Value
  val MD5, Cleartext, Ok = Value
}

trait AuthenticationChallengeMessage extends AuthenticationMessage {
  val challengeType: AuthenticationResponseType.AuthenticationResponseType
  val salt: Option[Array[Byte]]
}

case object AuthenticationChallengeCleartextMessage extends AuthenticationChallengeMessage {
  override val challengeType: AuthenticationResponseType = AuthenticationResponseType.Cleartext
  override val salt: Option[Array[Byte]] = None
}

case class AuthenticationChallengeMD5(private val _salt: Array[Byte]) extends AuthenticationChallengeMessage {
  override val challengeType: AuthenticationResponseType = AuthenticationResponseType.MD5
  override val salt: Option[Array[Byte]] = Some(_salt)
}