/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.codec

import akka.NotUsed
import akka.stream.scaladsl.Source
import de.envisia.postgresql.impl.engine.PostgreClientMessage
import de.envisia.postgresql.message.backend.PostgreServerMessage

import scala.concurrent.Future

sealed trait Message
case class SimpleMessage(msg: PostgreServerMessage) extends Message
case class MultiMessage(source: Source[PostgreServerMessage, NotUsed]) extends Message

sealed trait Dispatch
case class SimpleDispatch(msg: PostgreClientMessage)
case class ReturnDispatch(msg: PostgreClientMessage, future: Future[Message])