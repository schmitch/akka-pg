/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.backend

import de.envisia.postgresql.message.KindedMessage

object ServerMessage {
  final val Authentication: Byte = 'R'
  final val BackendKeyData: Byte = 'K'
  final val Bind: Byte = 'B'
  final val BindComplete: Byte = '2'
  final val CommandComplete: Byte = 'C'
  final val Close: Byte = 'X'
  final val CloseStatementOrPortal: Byte = 'C'
  final val CloseComplete: Byte = '3'
  final val DataRow: Byte = 'D'
  final val Describe: Byte = 'D'
  final val Error: Byte = 'E'
  final val Execute: Byte = 'E'
  final val EmptyQueryString: Byte = 'I'
  final val NoData: Byte = 'n'
  final val Notice: Byte = 'N'
  final val NotificationResponse: Byte = 'A'
  final val ParameterStatus: Byte = 'S'
  final val Parse: Byte = 'P'
  final val ParseComplete: Byte = '1'
  final val PasswordMessage: Byte = 'p'
  final val PortalSuspended: Byte = 's'
  final val Query: Byte = 'Q'
  final val RowDescription: Byte = 'T'
  final val ReadyForQuery: Byte = 'Z'
  final val Sync: Byte = 'S'
}

trait PostgreServerMessage

trait ServerMessage extends KindedMessage with PostgreServerMessage {
  val kind: Int
}

