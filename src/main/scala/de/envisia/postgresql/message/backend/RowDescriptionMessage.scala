/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.backend

import de.envisia.postgresql.message.ColumnData

case class PostgreSQLColumnData(
    name: String,
    tableObjectId: Int,
    columnNumber: Int,
    dataType: Int,
    dataTypeSize: Long,
    dataTypeModifier: Int,
    fieldFormat: Int
) extends ColumnData

case class RowDescriptionMessage(columnDatas: Array[PostgreSQLColumnData]) extends ServerMessage {
  override val kind: Int = ServerMessage.RowDescription
}