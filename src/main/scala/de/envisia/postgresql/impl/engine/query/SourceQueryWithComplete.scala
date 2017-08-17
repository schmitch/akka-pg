/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine.query

trait SourceQueryWithComplete[T] {

  def offer(elem: T): Unit

  def complete(): Unit

  def fail(ex: Throwable): Unit

}
