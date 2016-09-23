/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message

import akka.stream.scaladsl.SourceQueueWithComplete
import de.envisia.postgresql.impl.engine.PostgreClientMessage
import de.envisia.postgresql.impl.engine2.OutMessage
import de.envisia.postgresql.message.backend.PostgreServerMessage

import scala.concurrent.Promise

sealed trait Message

case class QueryMessage(msg: PostgreServerMessage) extends Message
case class DelayedMessage(msg: PostgreClientMessage, promise: Option[SourceQueueWithComplete[OutMessage]]) extends Message
// case class SimpleMessage(msg: PostgreClientMessage) extends Message
// case class TransactionMessage(msg: PostgreClientMessage, txId: Int) extends Message
// case class ServerStateMessage(msg: PostgreServerMessage) extends Message
