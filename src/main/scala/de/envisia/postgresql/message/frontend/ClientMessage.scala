/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.frontend

import de.envisia.postgresql.impl.engine.PostgreClientMessage
import de.envisia.postgresql.message.KindedMessage

object ClientMessage {

  final val ClientProtocolVersion = 0x09
  // COM_STATISTICS
  final val Quit = 0x01
  // COM_QUIT
  final val Query = 0x03
  // COM_QUERY
  final val PreparedStatementPrepare = 0x16
  // COM_STMT_PREPARE
  final val PreparedStatementExecute = 0x17
  // COM_STMT_EXECUTE
  final val PreparedStatementSendLongData = 0x18
  // COM_STMT_SEND_LONG_DATA
  final val AuthSwitchResponse = 0xfe // AuthSwitchRequest

}

trait ClientMessage extends KindedMessage with PostgreClientMessage {
  val kind: Int
}