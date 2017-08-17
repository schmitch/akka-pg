/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.codec

import de.envisia.postgresql.impl.engine.PostgreClientMessage
import de.envisia.postgresql.impl.engine.query.PostgresQuery
import de.envisia.postgresql.message.backend.PostgreServerMessage

import scala.concurrent.Promise

sealed trait Message
case class SimpleMessage(msg: PostgreServerMessage) extends Message

sealed trait Dispatch
case class SimpleDispatch(msg: PostgreClientMessage) extends Dispatch
case class ReturnDispatch(msg: PostgreClientMessage, promise: Promise[PostgresQuery]) extends Dispatch