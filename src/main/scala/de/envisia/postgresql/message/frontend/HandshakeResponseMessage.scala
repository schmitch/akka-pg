/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.frontend

import java.nio.charset.Charset

case class HandshakeResponseMessage(
    username: String,
    charset: Charset,
    seed: Array[Byte],
    authenticationMethod: String,
    password: Option[String] = None,
    database: Option[String] = None
) extends ClientMessage {
  override val kind: Int = ClientMessage.ClientProtocolVersion
}
