/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.backend

case class ProcessData(processId: Int, secretKey: Int) extends ServerMessage {
  override val kind: Int = ServerMessage.BackendKeyData
}
