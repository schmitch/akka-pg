/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine3

import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, BidiShape, Inlet, Outlet }
import de.envisia.postgresql.message.backend._
import de.envisia.postgresql.message.frontend.{ CredentialMessage, StartupMessage }

import scala.collection.mutable

private[engine3] class PostgreStage(database: String, username: Option[String], password: Option[String])
    extends GraphStage[BidiShape[PostgreServerMessage, PostgreServerMessage, PostgreClientMessage, PostgreClientMessage]] {

  private val serverIn = Inlet[PostgreServerMessage]("PGServer.in")
  private val serverOut = Outlet[PostgreServerMessage]("PGServer.out")
  private val clientIn = Inlet[PostgreClientMessage]("PGClient.in")
  private val clientOut = Outlet[PostgreClientMessage]("PGClient.out")

  override lazy val shape: BidiShape[PostgreServerMessage, PostgreServerMessage, PostgreClientMessage, PostgreClientMessage] =
    BidiShape.of(serverIn, serverOut, clientIn, clientOut)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var readyForQuery: Boolean = false
    private var init: Boolean = true
    private val params: mutable.Map[String, String] = mutable.Map.empty[String, String]
    private var transactionStatus: Char = '0'
    private var pid: Int = 0
    private var secretKey: Int = 0

    setHandler(serverIn, new InHandler {
      override def onPush(): Unit = {
        val elem = grab(serverIn)
        elem match {
          case pd: ProcessData =>
            pid = pd.processId
            secretKey = pd.secretKey
            pull(serverIn)
          case AuthenticationOkMessage => pull(serverIn)
          case challenge: AuthenticationChallengeMessage =>
            push(clientOut, CredentialMessage(username.orNull, password.orNull, challenge.challengeType, challenge.salt))
            pull(serverIn)
          case ParameterStatusMessage(key, value) =>
            params += ((key, value))
            pull(serverIn)
          case r: ReadyForQueryMessage =>
            transactionStatus = r.transactionStatus
            readyForQuery = true
            println("readForQuery")
            pull(serverIn)
            pull(clientIn)
          case c: CommandCompleteMessage =>
            println(s"CommandComplete: $c")
            pull(serverIn)
          case _ => push(serverOut, elem)
        }
      }
    })

    setHandler(serverOut, new OutHandler {
      override def onPull(): Unit = {
        pull(serverIn)
      }
    })

    setHandler(clientIn, new InHandler {
      override def onPush(): Unit = {
        if (readyForQuery) {
          // TODO: grab all listen elements and save them
          val elem = grab(clientIn)
          push(clientOut, elem)
        }
      }
    })

    setHandler(clientOut, new OutHandler {
      override def onPull(): Unit = {
        if (init) {
          push(clientOut, StartupMessage(List(
            "database" -> database,
            "user" -> username.orNull,
            "client_encoding" -> "UTF8",
            "DateStyle" -> "ISO",
            "extra_float_digits" -> "2",
            "application_name" -> "envisia-akka-client"
          )))
          init = false
        } else if (readyForQuery && isAvailable(clientIn)) {
          pull(clientIn)
        }
      }
    })

  }

}
