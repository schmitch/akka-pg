/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import akka.stream.scaladsl.Source
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, BidiShape, Inlet, Outlet }
import de.envisia.postgresql.codec._
import de.envisia.postgresql.message.backend._
import de.envisia.postgresql.message.frontend.{ CredentialMessage, StartupMessage }
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.Promise

private[engine] class PostgreStage(database: String, username: Option[String], password: Option[String])
    extends GraphStage[BidiShape[PostgreServerMessage, Message, Dispatch, PostgreClientMessage]] {

  private val logger = LoggerFactory.getLogger(classOf[ConnectionManagement])

  private val serverIn = Inlet[PostgreServerMessage]("PGServer.in")
  private val serverOut = Outlet[Message]("PGServer.out")
  private val clientIn = Inlet[Dispatch]("PGClient.in")
  private val clientOut = Outlet[PostgreClientMessage]("PGClient.out")

  override lazy val shape: BidiShape[PostgreServerMessage, Message, Dispatch, PostgreClientMessage] =
    BidiShape.of(serverIn, serverOut, clientIn, clientOut)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var readyForQuery: Boolean = false
    private var init: Boolean = true
    private val params: mutable.Map[String, String] = mutable.Map.empty[String, String]
    private var transactionStatus: Char = '0'
    private var pid: Int = 0
    private var secretKey: Int = 0
    private var promise: Promise[Message] = null

    override def preStart(): Unit = {
      debug(s"preStart: ${isAvailable(clientOut)}")
    }

    private def resolvePromise(msg: Message) = {
      if (promise != null) {
        if (!promise.isCompleted) {
          promise.success(msg)
          promise = null
        }
      }
    }

    private def failPromise(t: Throwable) = {
      if (promise != null) {
        if (!promise.isCompleted) {
          promise.failure(t)
          promise = null
        }
      }
    }

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
            // TODO: no if/else needed, probably
            if (promise != null) {
              promise = null
            }
            debug("readForQuery")
            pull(serverIn)
            pull(clientIn)
          case c: CommandCompleteMessage =>
            // resolves any outstanding promises
            resolvePromise(SimpleMessage(elem))
            debug(s"CommandComplete: $c")
            pull(serverIn)
          case n: NotificationResponse =>
            push(serverOut, SimpleMessage(n))
          case _ =>
            // resolves any outstanding promises
            resolvePromise(SimpleMessage(elem))
            push(serverOut, MultiMessage(Source.single(elem)))
        }
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        // if the stage fails and there is any outstanding promise
        // we need to resolve it
        failPromise(ex)
        failStage(ex)
      }

      override def onUpstreamFinish(): Unit = {
        failPromise(new Exception("Connection closed"))
        completeStage()
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
          grab(clientIn) match {
            case SimpleDispatch(elem) => push(clientOut, elem)
            case ReturnDispatch(elem, p) =>
              promise = p
              push(clientOut, elem)
          }
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

    private def debug(msg: String): Unit = {
      logger.debug(msg)
    }

  }

}
