/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap.KeySetView
import java.util.concurrent.{ ConcurrentHashMap, ConcurrentLinkedQueue }

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ BidiFlow, BroadcastHub, Flow, Keep, RestartFlow, Sink, Source, Tcp }
import akka.util.ByteString
import akka.{ Done, NotUsed }
import de.envisia.postgresql.codec._
import de.envisia.postgresql.message.backend.NotificationResponse
import de.envisia.postgresql.message.frontend.QueryMessage
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success }

private[engine] class PostgresConnection(
    host: String,
    port: Int,
    database: String,
    username: Option[String],
    password: Option[String],
    timeout: FiniteDuration,
    bufferSize: Int,
    killSwitch: SharedKillSwitch
)(implicit actorSystem: ActorSystem, mat: Materializer) extends PostgresQueryInterface {

  private implicit val ec: ExecutionContext = mat.executionContext

  @volatile
  private var state: Tcp.OutgoingConnection = _
  private var init = true

  private val logger = LoggerFactory.getLogger(classOf[PostgresConnection])

  private val decider: Supervision.Decider = { t =>
    // useful for Portocol Errors, that should not do any reconnect
    logger.error("Protocol Error", t)
    Supervision.Resume
  }

  private val channels: KeySetView[String, java.lang.Boolean] = ConcurrentHashMap.newKeySet()
  // should never be called inside the decider, since basically this will only be
  // for tracking the initial connection, if the connection succeds any error will be handled
  // by the PostgreStage/PostgreProtocol
  private val queryQueue = new ConcurrentLinkedQueue[Promise[Message]]()

  private def connectionFlow: Flow[InMessage, OutMessage, NotUsed] = {
    val engine = EngineVar(host, port, database, username, password, timeout)
    val socketAddress = InetSocketAddress.createUnresolved(engine.host, engine.port)

    // Full TCP Flow Client that will be restarted whenever needed by the RestartFlow
    val tcpFlow = Tcp().outgoingConnection(socketAddress, connectTimeout = engine.timeout)
      .join(new PostgreProtocol(StandardCharsets.UTF_8).serialization)
      .join(new PostgreStage(engine.database, engine.username, engine.password))
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
      .mapMaterializedValue(_.onComplete {
        case Success(d) =>
          state = d
          logger.debug(s"Connected $d")
          // Replay any Query that needs to be replayed
          if (!init) {
            channels.forEach(listen) // FIXME: if we have too many channels, we might need to make this better
          } else {
            init = false
          }
        case Failure(t) =>
          logger.error("Connection Failure", t)
          queryQueue.forEach(promise => {
            if (!promise.isCompleted) {
              promise.tryFailure(t)
              queryQueue.remove(promise)
            }
          })
      })

    RestartFlow.withBackoff(100.milliseconds, 1.second, 0.2)(() => tcpFlow)
  }

  private val ((queue, killSwitches), source) = Source.queue[InMessage](bufferSize, OverflowStrategy.dropNew)
    .filter {
      // only allow queries to pass that are not completed
      case ReturnDispatch(_, promise) =>
        queryQueue.remove(promise) // removes all queries from the queue
        !promise.isCompleted
      case SimpleDispatch(_) => true
    }
    .viaMat(connectionFlow)(Keep.left)
    .viaMat(killSwitch.flow)(Keep.both)
    .toMat(BroadcastHub.sink[OutMessage](bufferSize = bufferSize))(Keep.both)
    .run()

  // Default Sink
  source.runWith(Sink.ignore)

  def newSource(buffer: Int = bufferSize, timeout: FiniteDuration = 5.seconds): Source[NotificationResponse, UniqueKillSwitch] = {
    // Notifications should only be allowed to a single Backend
    source.viaMat(KillSwitches.single)(Keep.right).filter {
      case SimpleMessage(pgs) => pgs match {
        case _: NotificationResponse => true
        case _ => false
      }
      case _ => false
    }.map {
      case SimpleMessage(pgs) => pgs match {
        case n: NotificationResponse => n
        case _ => throw new IllegalStateException("not a valid state")
      }
      case _ => throw new IllegalStateException("not a valid state")
    }.backpressureTimeout(timeout).buffer(buffer * 2, OverflowStrategy.dropNew)
  }

  def executeQuery(query: String): Future[Message] = {
    val promise = Promise[Message]()
    queryQueue.add(promise)
    // FIXME: check the offer responses
    queue.offer(ReturnDispatch(QueryMessage(query), promise))
    promise.future
  }

  def checkState: Future[Tcp.OutgoingConnection] = {
    val promise = Promise[Tcp.OutgoingConnection]()
    if (state == null) {
      promise.tryFailure(new Exception("Client is not connected"))
    } else {
      promise.success(state)
    }
    promise.future
  }

  /**
   * Listen to Postgres Notifications
   * This command will add the channel to replayeable fields
   *
   * @param channel a channel to listen to
   * @return a Postgres Query Message, which will yield for success
   */
  def listen(channel: String): Future[Message] = {
    channels.add(channel)
    executeQuery(s"LISTEN $channel;")
  }

  /**
   * Unlisten to Postgres Notifications
   * This command will remove the channel from the replayeable fields
   *
   * @param channel channels to unlisten to
   * @return a Postgres Query Message, which will yield for success
   */
  def unlisten(channel: String*): Future[Message] = {
    channel.foreach(channels.remove)
    executeQuery(s"UNLISTEN ${channel.mkString(", ")};")
  }

  def shutdown(): Future[Done] = {
    killSwitches.shutdown()
    queue.watchCompletion()
  }

}

object PostgresConnection {

  def apply(
    host: String,
    port: Int,
    database: String,
    username: Option[String],
    password: Option[String],
    timeout: FiniteDuration = 5.seconds
  )(implicit actorSystem: ActorSystem, mat: Materializer): PostgresConnection = {
    val killSwitch = KillSwitches.shared("postgres")
    new PostgresConnection(
      host,
      port,
      database,
      username,
      password,
      timeout,
      64, // bufferSize
      killSwitch
    )
  }

}