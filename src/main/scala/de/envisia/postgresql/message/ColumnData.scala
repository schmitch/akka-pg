/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message

trait ColumnData {
  def name: String
  def dataType: Int
  def dataTypeSize: Long
}
