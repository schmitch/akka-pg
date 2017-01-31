/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.parsers

import java.nio.ByteBuffer

import de.envisia.akka.utils.ByteBufferUtils
import de.envisia.postgresql.message.backend.{ AuthenticationChallengeCleartextMessage, AuthenticationChallengeMD5, AuthenticationOkMessage, ServerMessage }

object AuthenticationStartupParser extends MessageParser {

  import ByteBufferUtils._

  final val AuthenticationOk = 0
  final val AuthenticationKerberosV5 = 2
  final val AuthenticationCleartextPassword = 3
  final val AuthenticationMD5Password = 5
  final val AuthenticationSCMCredential = 6
  final val AuthenticationGSS = 7
  final val AuthenticationGSSContinue = 8
  final val AuthenticationSSPI = 9

  override def parseMessage(msg: ByteBuffer): ServerMessage = {
    msg.getInt match {
      case AuthenticationOk => AuthenticationOkMessage
      case AuthenticationCleartextPassword => AuthenticationChallengeCleartextMessage
      case AuthenticationMD5Password => AuthenticationChallengeMD5(msg.asArray)
      case _ => throw new Exception("UnsupportedAuthenticationMethodException")
    }
  }

}
