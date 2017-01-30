/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import scala.concurrent.duration.FiniteDuration

case class EngineVar(
    host: String,
    port: Int,
    database: String,
    username: Option[String],
    password: Option[String],
    timeout: FiniteDuration
)
