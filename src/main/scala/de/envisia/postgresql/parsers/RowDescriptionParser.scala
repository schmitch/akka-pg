/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.ByteBuffer
import java.nio.charset.Charset

import de.envisia.akka.utils.ByteBufferUtils
import de.envisia.postgresql.message.backend.{PostgreSQLColumnData, RowDescriptionMessage, ServerMessage}

/**
 *RowDescription (B)
 *Byte1('T')
 *Identifies the message as a row description.
 *Int32
 *Length of message contents in bytes, including self.
 *Int16
 *Specifies the number of fields in a row (can be zero).
 *Then, for each field, there is the following:
 *String
 *The field name.
 *Int32
 *If the field can be identified as a column of a specific table, the object ID of the table; otherwise zero.
 *Int16
 *If the field can be identified as a column of a specific table, the attribute number of the column; otherwise zero.
 *Int32
 *The object ID of the field's data type.
 *Int16
 *The data type size (see pg_type.typlen). Note that negative values denote variable-width types.
 *Int32
 *The type modifier (see pg_attribute.atttypmod). The meaning of the modifier is type-specific.
 *Int16
 *The format code being used for the field. Currently will be zero (text) or one (binary). In a RowDescription returned from the statement variant of Describe, the format code is not yet known and will always be zero.
 *
 */
class RowDescriptionParser(charset: Charset) extends MessageParser {

  override def parseMessage(b: ByteBuffer): ServerMessage = {

    val columnsCount = b.getShort
    val columns = new Array[PostgreSQLColumnData](columnsCount)

    0.until(columnsCount).foreach {
      index =>
        columns(index) = PostgreSQLColumnData(
          name = ByteBufferUtils.readCString(b, charset),
          tableObjectId = b.getShort(),
          columnNumber = b.getShort(),
          dataType = b.getInt(),
          dataTypeSize = b.getShort(),
          dataTypeModifier = b.getInt(),
          fieldFormat = b.getShort()
        )
    }

    RowDescriptionMessage(columns)
  }

}