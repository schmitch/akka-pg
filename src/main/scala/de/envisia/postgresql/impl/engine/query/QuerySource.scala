/*
 * Copyright (C) 2017. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.impl.engine.query

import akka.stream._
import akka.stream.stage.{ AsyncCallback, GraphStageLogic, GraphStageWithMaterializedValue, OutHandler }

import scala.collection.mutable
import scala.concurrent.Future

private[postgresql] object QuerySource {
  sealed trait Input[+T]
  final case class Offer[+T](elem: T) extends Input[T]
  case object Completion extends Input[Nothing]
  final case class Failure(ex: Throwable) extends Input[Nothing]
}

private[postgresql] final class QuerySource[T]() extends GraphStageWithMaterializedValue[SourceShape[T], SourceQueryWithComplete[T]] {

  import QuerySource._

  val out: Outlet[T] = Outlet[T]("QuerySource.out")
  override val shape: SourceShape[T] = SourceShape.of(out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, SourceQueryWithComplete[T]) = {
    val stageLogic = new GraphStageLogic(shape) with CallbackWrapper[Input[T]] with OutHandler {
      var terminating = false
      var buffer: mutable.Queue[T] = _

      override def preStart(): Unit = {
        buffer = mutable.Queue[T]()
        initCallback(callback.invoke)
      }

      private val callback: AsyncCallback[Input[T]] = getAsyncCallback {
        case Offer(elem) ⇒
          buffer += elem
          if (isAvailable(out)) push(out, buffer.dequeue())

        case Completion ⇒
          if (buffer.nonEmpty) terminating = true
          else {
            completeStage()
          }

        case Failure(ex) ⇒
          failStage(ex)
      }

      override def onPull(): Unit = {
        if (buffer.nonEmpty) {
          push(out, buffer.dequeue())
          if (terminating && buffer.isEmpty) {
            completeStage()
          }
        }
      }

      setHandler(out, this)
    }

    (stageLogic, new SourceQueryWithComplete[T] {
      override def offer(elem: T): Unit = stageLogic.invoke(QuerySource.Offer(elem))
      override def complete(): Unit = stageLogic.invoke(QuerySource.Completion)
      override def fail(ex: Throwable): Unit = stageLogic.invoke(QuerySource.Failure(ex))
    })
  }
}