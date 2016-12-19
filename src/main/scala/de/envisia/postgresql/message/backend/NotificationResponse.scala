/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.backend

case class NotificationResponse(backendPid: Int, channel: String, payload: String) extends ServerMessage {
  override val kind: Int = ServerMessage.NotificationResponse
}