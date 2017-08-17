/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import akka.stream._
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.stream.stage._
import de.envisia.postgresql.codec._
import de.envisia.postgresql.impl.engine.query.{ PostgresQuery, QuerySource, SourceQueryWithComplete }
import de.envisia.postgresql.message.backend._
import de.envisia.postgresql.message.frontend.{ CredentialMessage, StartupMessage }
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Promise }

private[postgresql] class PostgreStage(
    database: String,
    username: Option[String],
    password: Option[String]
)(implicit executionContext: ExecutionContext, mat: Materializer)
    extends GraphStage[BidiShape[PostgreServerMessage, Message, Dispatch, PostgreClientMessage]] {

  private val logger = LoggerFactory.getLogger(classOf[PostgreStage])

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
    // These variables needs to be cleaned up:
    private var promise: Promise[PostgresQuery] = _
    private var queryInProgress: SourceQueryWithComplete[DataRowMessage] = _
    private var rowDescription: RowDescriptionMessage = _
    private var commandPromise: Promise[CommandCompleteMessage] = _

    override def preStart(): Unit = {
      logger.debug(s"preStart: ${isAvailable(clientOut)}")
    }

    private def resolvePromise(msg: PostgresQuery): Unit = {
      if (promise != null) {
        if (!promise.isCompleted) {
          promise.success(msg)
          promise = null
        }
      }
    }

    private def failPromise(t: Throwable): Unit = {
      if (promise != null) {
        if (!promise.isCompleted) {
          promise.failure(t)
          promise = null
        }
      }
    }

    private def resetQuery(message: String): Unit = {
      if (commandPromise != null) {
        if (!commandPromise.isCompleted) {
          commandPromise.tryFailure(new Exception(message))
          commandPromise = null
        }
      }
      if (queryInProgress != null) {
        queryInProgress.fail(new Exception(message))
        queryInProgress = null
      }
    }

    setHandler(serverIn, new InHandler {
      override def onPush(): Unit = {
        val elem = grab(serverIn)
        logger.debug(s"Server Command: $elem")
        elem match {
          case se: ErrorMessage =>
            logger.debug(s"Server Error")
            failStage(new Exception(s"Server Error: ${se.fields}"))
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
            resetQuery("command not completed correctly")
            rowDescription = null // resets current row description
            transactionStatus = r.transactionStatus
            readyForQuery = true
            // TODO: no if/else needed, probably
            if (promise != null) {
              promise = null
            }
            logger.debug("readForQuery")
            pull(serverIn)
            pull(clientIn)
          case c: CommandCompleteMessage =>
            // resolves any outstanding promises
            if (commandPromise != null) {
              commandPromise.trySuccess(c)
              commandPromise = null
            }
            if (queryInProgress != null) {
              queryInProgress.complete()
              queryInProgress = null
            }
            pull(serverIn)
          case n: NotificationResponse =>
            push(serverOut, SimpleMessage(n))
          case r: RowDescriptionMessage => // incoming query
            rowDescription = r
            commandPromise = Promise[CommandCompleteMessage]()
            push(serverOut, SimpleMessage(r))
          case drm: DataRowMessage =>
            if (queryInProgress != null) {
              queryInProgress.offer(drm)
            } else {
              // FIXME: https://github.com/akka/akka/issues/22587
              // currently talking with another Source is impossible without first materializing
              // the stream and creating a new source with the sink
              val (queue, publisher) = Source.fromGraph(new QuerySource[DataRowMessage]())
                  .toMat(Sink.asPublisher(false))(Keep.both)
                  .run()
              queryInProgress = queue
              queryInProgress.offer(drm)
              resolvePromise(Source.fromPublisher(publisher).mapMaterializedValue(_ => commandPromise.future))
            }
            push(serverOut, SimpleMessage(drm))
          case _ =>
            // resolves any outstanding promises
            push(serverOut, SimpleMessage(elem))
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
  }

}
