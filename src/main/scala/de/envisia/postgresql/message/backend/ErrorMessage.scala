/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.backend

case class ErrorMessage(override val fields: Map[Char, String]) extends InformationMessage(ServerMessage.Error, fields)
