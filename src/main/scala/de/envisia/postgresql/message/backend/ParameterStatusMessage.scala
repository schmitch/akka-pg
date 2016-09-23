/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.backend

case class ParameterStatusMessage(key: String, value: String) extends ServerMessage {
  override val kind: Int = ServerMessage.ParameterStatus
}
