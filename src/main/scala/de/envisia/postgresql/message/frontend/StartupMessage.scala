/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message.frontend

import de.envisia.postgresql.impl.engine3.PostgreClientMessage

case class StartupMessage(parameters: List[(String, Any)]) extends PostgreClientMessage
