/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import akka.stream.scaladsl.Source
import de.envisia.postgresql.message.backend.{ CommandCompleteMessage, DataRowMessage }

import scala.concurrent.Future

package object query {

  private[postgresql]type PostgresQuery = Source[DataRowMessage, Future[CommandCompleteMessage]]

}
