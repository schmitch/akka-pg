/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.frontend

import de.envisia.postgresql.message.backend.{ AuthenticationResponseType, ServerMessage }

private [postgresql] case class CredentialMessage(
    username: String,
    password: String,
    authenticationType: AuthenticationResponseType.AuthenticationResponseType,
    salt: Option[Array[Byte]]
) extends ClientMessage {
  override val kind: Int = ServerMessage.PasswordMessage
}
