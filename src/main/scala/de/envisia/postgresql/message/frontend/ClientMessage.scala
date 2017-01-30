/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.frontend

import de.envisia.postgresql.impl.engine.PostgreClientMessage
import de.envisia.postgresql.message.KindedMessage

private [postgresql] object ClientMessage {

  final val ClientProtocolVersion: Int = 0x09
  // COM_STATISTICS
  final val Quit: Int = 0x01
  // COM_QUIT
  final val Query: Int = 0x03
  // COM_QUERY
  final val PreparedStatementPrepare: Int = 0x16
  // COM_STMT_PREPARE
  final val PreparedStatementExecute: Int = 0x17
  // COM_STMT_EXECUTE
  final val PreparedStatementSendLongData: Int = 0x18
  // COM_STMT_SEND_LONG_DATA
  final val AuthSwitchResponse: Int = 0xfe // AuthSwitchRequest

}

private[postgresql] trait ClientMessage extends KindedMessage with PostgreClientMessage {
  val kind: Int
}