/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import de.envisia.postgresql.codec.{ Message, ReturnDispatch }
import de.envisia.postgresql.message.frontend.QueryMessage

import scala.concurrent.{ Future, Promise }

class PostgresTransaction extends PostgresQueryInterface {

  def executeQuery(query: String): Future[Message] = {
    ???
  }

}
