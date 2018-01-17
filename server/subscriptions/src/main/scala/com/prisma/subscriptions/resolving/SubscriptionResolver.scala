package com.prisma.subscriptions.resolving

import java.util.concurrent.TimeUnit

import com.prisma.api.database.DataItem
import com.prisma.api.mutations.GraphcoolDataTypes
import com.prisma.api.subscriptions.SubscriptionExecutor
import com.prisma.shared.models.ModelMutationType.ModelMutationType
import com.prisma.shared.models.{Model, ModelMutationType, Project}
import com.prisma.subscriptions.SubscriptionDependencies
import com.prisma.subscriptions.metrics.SubscriptionMetrics.handleDatabaseEventTimer
import com.prisma.subscriptions.resolving.SubscriptionsManagerForModel.Requests.StartSubscription
import com.prisma.subscriptions.util.PlayJson
import play.api.libs.json._

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

case class SubscriptionResolver(
    project: Project,
    model: Model,
    mutationType: ModelMutationType,
    subscription: StartSubscription,
    scheduler: akka.actor.Scheduler
)(
    implicit dependencies: SubscriptionDependencies,
    ec: ExecutionContext
) {
  import DatabaseEvents._

  def handleDatabaseMessage(event: String): Future[Option[JsValue]] = {
    import DatabaseEventReaders._
    val dbEvent = PlayJson.parse(event).flatMap { json =>
      mutationType match {
        case ModelMutationType.Created => json.validate[DatabaseCreateEvent]
        case ModelMutationType.Updated => json.validate[DatabaseUpdateEvent]
        case ModelMutationType.Deleted => json.validate[DatabaseDeleteEvent]
      }
    }

    dbEvent match {
      case JsError(_) =>
        Future.successful(None)

      case JsSuccess(event, _) =>
        handleDatabaseEventTimer.timeFuture(project.id) {
          delayed(handleDatabaseMessage(event))
        }
    }
  }

  // In production we read from db replicas that can be up to 20 ms behind master. We add 35 ms buffer
  // Please do not remove this artificial delay!
  def delayed[T](fn: => Future[T]): Future[T] = akka.pattern.after(Duration(35, TimeUnit.MILLISECONDS), using = scheduler)(fn)

  def handleDatabaseMessage(event: DatabaseEvent): Future[Option[JsValue]] = {
    event match {
      case e: DatabaseCreateEvent => handleDatabaseCreateEvent(e)
      case e: DatabaseUpdateEvent => handleDatabaseUpdateEvent(e)
      case e: DatabaseDeleteEvent => handleDatabaseDeleteEvent(e)
    }
  }

  def handleDatabaseCreateEvent(event: DatabaseCreateEvent): Future[Option[JsValue]] = {
    executeQuery(event.nodeId, previousValues = None, updatedFields = None)
  }

  def handleDatabaseUpdateEvent(event: DatabaseUpdateEvent): Future[Option[JsValue]] = {
    val values         = GraphcoolDataTypes.fromJson(event.previousValues, model.fields)
    val previousValues = DataItem(event.nodeId, values)

    executeQuery(event.nodeId, Some(previousValues), updatedFields = Some(event.changedFields.toList))
  }

  def handleDatabaseDeleteEvent(event: DatabaseDeleteEvent): Future[Option[JsValue]] = {
    val values         = GraphcoolDataTypes.fromJson(event.node, model.fields)
    val previousValues = DataItem(event.nodeId, values)

    executeQuery(event.nodeId, Some(previousValues), updatedFields = None)
  }

  def executeQuery(nodeId: String, previousValues: Option[DataItem], updatedFields: Option[List[String]]): Future[Option[JsValue]] = {
    val variables: spray.json.JsValue = subscription.variables match {
      case None =>
        spray.json.JsObject.empty

      case Some(vars) =>
        val str = vars.toString
        VariablesParser.parseVariables(str)
    }

    SubscriptionExecutor
      .execute(
        project = project,
        model = model,
        mutationType = mutationType,
        previousValues = previousValues,
        updatedFields = updatedFields,
        query = subscription.query,
        variables = variables,
        nodeId = nodeId,
        requestId = s"subscription:${subscription.sessionId}:${subscription.id.asString}",
        operationName = subscription.operationName,
        skipPermissionCheck = false,
        alwaysQueryMasterDatabase = false
      )
      .map { x =>
        x.map { sprayJsonResult =>
          Json.parse(sprayJsonResult.toString)
        }
      }
  }
}
