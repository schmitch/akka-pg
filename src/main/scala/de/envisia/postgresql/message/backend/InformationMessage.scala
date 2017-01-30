/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.backend

object InformationMessage {

  val Severity: Char = 'S'
  val SQLState: Char = 'C'
  val Message: Char = 'M'
  val Detail: Char = 'D'
  val Hint: Char = 'H'
  val Position: Char = 'P'
  val InternalQuery: Char = 'q'
  val Where: Char = 'W'
  val File: Char = 'F'
  val Line: Char = 'L'
  val Routine: Char = 'R'

  val Fields: Map[Char, String] = Map(
    Severity -> "Severity",
    SQLState -> "SQLSTATE",
    Message -> "Message",
    Detail -> "Detail",
    Hint -> "Hint",
    Position -> "Position",
    InternalQuery -> "Internal Query",
    Where -> "Where",
    File -> "File",
    Line -> "Line",
    Routine -> "Routine"
  )

  def fieldName(name: Char): String = Fields.getOrElse(name, {
    name.toString
  })

}

abstract class InformationMessage(messageType: Byte, val fields: Map[Char, String]) extends ServerMessage {

  override val kind: Int = ServerMessage.Error

  def message: String = this.fields('M')

  override def toString: String = {
    "%s(fields=%s)".format(this.getClass.getSimpleName, fields.map {
      pair => InformationMessage.fieldName(pair._1) -> pair._2
    })
  }

}
