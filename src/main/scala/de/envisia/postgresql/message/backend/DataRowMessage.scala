/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.backend

import akka.util.ByteString

case class DataRowMessage(values: Array[ByteString]) extends ServerMessage {
  override val kind: Int = ServerMessage.DataRow
}