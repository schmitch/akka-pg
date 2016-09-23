/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl

import akka.NotUsed
import akka.stream.scaladsl.{ Source, SourceQueueWithComplete }
import de.envisia.postgresql.impl.engine.PostgreClientMessage
import de.envisia.postgresql.message
import de.envisia.postgresql.message.backend.PostgreServerMessage

import scala.concurrent.Promise

package object engine2 {

  private[postgresql] type InMessage = PostgreClientMessage
  private[postgresql] type OutMessage = PostgreServerMessage
  private[postgresql] type OutSource = Source[OutMessage, NotUsed]

  private[postgresql] type SourcedInMessage = (InMessage, Option[SourceQueueWithComplete[OutMessage]])
  private[postgresql] type SourcedOutMessage = (OutMessage, Option[SourceQueueWithComplete[OutMessage]])

  private[postgresql] type PromisedInMessage = (InMessage, Option[Promise[OutSource]])
  private[postgresql] type PromisedOutMessage = (OutMessage, Option[Promise[OutSource]])

}
