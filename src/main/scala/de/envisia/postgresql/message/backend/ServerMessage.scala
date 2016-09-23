/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.backend

import java.nio.charset.Charset

import akka.util.ByteString
import de.envisia.postgresql.message.KindedMessage

import scala.util.{ Success, Try }

object ServerMessage {
  final val Authentication = 'R'
  final val BackendKeyData = 'K'
  final val Bind = 'B'
  final val BindComplete = '2'
  final val CommandComplete = 'C'
  final val Close = 'X'
  final val CloseStatementOrPortal = 'C'
  final val CloseComplete = '3'
  final val DataRow = 'D'
  final val Describe = 'D'
  final val Error = 'E'
  final val Execute = 'E'
  final val EmptyQueryString = 'I'
  final val NoData = 'n'
  final val Notice = 'N'
  final val NotificationResponse = 'A'
  final val ParameterStatus = 'S'
  final val Parse = 'P'
  final val ParseComplete = '1'
  final val PasswordMessage = 'p'
  final val PortalSuspended = 's'
  final val Query = 'Q'
  final val RowDescription = 'T'
  final val ReadyForQuery = 'Z'
  final val Sync = 'S'
}

trait PostgreServerMessage

trait ServerMessage extends KindedMessage with PostgreServerMessage {
  val kind: Int
}

