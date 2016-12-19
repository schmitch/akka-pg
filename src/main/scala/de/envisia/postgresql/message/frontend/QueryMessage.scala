/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.frontend

import de.envisia.postgresql.message.backend.ServerMessage

class QueryMessage(val query: String) extends ClientMessage {
  override val kind: Int = ServerMessage.Query: Int
}