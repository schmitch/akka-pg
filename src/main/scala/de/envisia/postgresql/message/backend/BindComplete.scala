/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.backend

object BindComplete extends ServerMessage {
  override val kind: Int = ServerMessage.BindComplete
}

object CloseComplete extends ServerMessage {
  override val kind: Int = ServerMessage.CloseComplete
}

object EmptyQueryString extends ServerMessage {
  override val kind: Int = ServerMessage.EmptyQueryString
}

object NoData extends ServerMessage {
  override val kind: Int = ServerMessage.NoData
}

object ParseComplete extends ServerMessage {
  override val kind: Int = ServerMessage.ParseComplete
}
