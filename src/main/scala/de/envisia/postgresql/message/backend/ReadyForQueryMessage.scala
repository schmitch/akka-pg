/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.backend

case class ReadyForQueryMessage(transactionStatus: Char) extends ServerMessage {
  override val kind: Int = ServerMessage.ReadyForQuery
}

