/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import de.envisia.postgresql.codec.Message

import scala.concurrent.Future

trait PostgresQueryInterface {

  def executeQuery(query: String): Future[Message]

}
