package com.prisma.subscriptions.protocol

import akka.actor.{Actor, ActorRef, Stash}
import com.prisma.akkautil.{LogUnhandled, LogUnhandledExceptions}
import com.prisma.api.ApiMetrics
import com.prisma.auth.AuthImpl
import com.prisma.shared.models.Project
import com.prisma.subscriptions.SubscriptionDependencies
import com.prisma.subscriptions.helpers.ProjectHelper
import com.prisma.subscriptions.metrics.SubscriptionMetrics
import com.prisma.subscriptions.protocol.SubscriptionSessionActorV05.Internal.Authorization
import com.prisma.subscriptions.resolving.SubscriptionsManager.Requests.EndSubscription
import com.prisma.subscriptions.resolving.SubscriptionsManager.Responses.{
  CreateSubscriptionFailed,
  CreateSubscriptionSucceeded,
  ProjectSchemaChanged,
  SubscriptionEvent
}
import play.api.libs.json._
import sangria.parser.QueryParser

object SubscriptionSessionActor {
  object Internal {
    case class Authorization(token: Option[String])

    // see https://github.com/apollographql/subscriptions-transport-ws/issues/174
    def extractOperationName(operationName: Option[String]): Option[String] = operationName match {
      case Some("") => None
      case x        => x
    }
  }
}

case class SubscriptionSessionActor(
    sessionId: String,
    projectId: String,
    subscriptionsManager: ActorRef
)(implicit dependencies: SubscriptionDependencies)
    extends Actor
    with LogUnhandled
    with LogUnhandledExceptions
    with Stash {

  import SubscriptionMetrics._
  import SubscriptionProtocolV07.Requests._
  import SubscriptionProtocolV07.Responses._
  import akka.pattern.pipe
  import com.prisma.subscriptions.resolving.SubscriptionsManager.Requests.CreateSubscription
  import context.dispatcher

  val reporter = dependencies.reporter

  override def preStart() = {
    super.preStart()
    activeSubcriptionSessions.inc
    pipe(ProjectHelper.resolveProject(projectId)(dependencies, context.system, context.dispatcher)) to self
  }

  override def postStop(): Unit = {
    super.postStop()
    activeSubcriptionSessions.dec
  }

  override def receive: Receive = logUnhandled {
    case project: Project =>
      context.become(waitingForInit(project))
      unstashAll()

    case akka.actor.Status.Failure(e) =>
      e.printStackTrace()
      context.stop(self)

    case _ =>
      stash()
  }

  def waitingForInit(project: Project): Receive = logUnhandled {
    case GqlConnectionInit(payload) =>
      ParseAuthorization.parseAuthorization(payload.getOrElse(Json.obj())) match {
        case Some(auth) =>
          val authResult = AuthImpl.verify(project.secrets, auth.token)
          if (authResult.isSuccess) {
            sendToWebsocket(GqlConnectionAck)
            context.become(initFinishedReceive(auth))
          } else {
            sendToWebsocket(GqlConnectionError("Authentication token is invalid."))
          }

        case None =>
          sendToWebsocket(GqlConnectionError("No Authorization field was provided in payload."))
      }

    case _: SubscriptionSessionRequest =>
      sendToWebsocket(GqlConnectionError("You have to send an init message before sending anything else."))
  }

  def initFinishedReceive(auth: Authorization): Receive = logUnhandled {
    case GqlStart(id, payload) =>
      handleStart(id, payload, auth)

    case GqlStop(id) =>
      subscriptionsManager ! EndSubscription(id, sessionId, projectId)

    case _: CreateSubscriptionSucceeded =>
    // NOOP
    case fail: CreateSubscriptionFailed =>
      sendToWebsocket(GqlError(fail.request.id, fail.errors.head.getMessage))

    case ProjectSchemaChanged(subscriptionId) =>
      sendToWebsocket(GqlError(subscriptionId, "Schema changed"))

    case SubscriptionEvent(subscriptionId, payload) =>
      ApiMetrics.subscriptionEventCounter.inc(projectId)
      val response = GqlData(subscriptionId, payload)
      sendToWebsocket(response)
  }

  private def handleStart(id: StringOrInt, payload: GqlStartPayload, auth: Authorization) = {
    val query = QueryParser.parse(payload.query)

    if (query.isFailure) {
      sendToWebsocket(GqlError(id, s"""the GraphQL Query was not valid"""))
    } else {
      val createSubscription = CreateSubscription(
        id = id,
        projectId = projectId,
        sessionId = sessionId,
        query = query.get,
        variables = payload.variables,
        authHeader = auth.token,
        operationName = SubscriptionSessionActor.Internal.extractOperationName(payload.operationName)
      )
      subscriptionsManager ! createSubscription
    }
  }

  private def sendToWebsocket(response: SubscriptionSessionResponse) = {
    context.parent ! response
  }
}

object ParseAuthorization {
  def parseAuthorization(jsObject: JsObject): Option[Authorization] = {

    def parseLowerCaseAuthorization = {
      (jsObject \ "authorization").validateOpt[String] match {
        case JsSuccess(authField, _) => Some(Authorization(authField))
        case JsError(_)              => None
      }
    }

    (jsObject \ "Authorization").validateOpt[String] match {
      case JsSuccess(Some(auth), _) => Some(Authorization(Some(auth)))
      case JsSuccess(None, _)       => parseLowerCaseAuthorization
      case JsError(_)               => None
    }
  }
}
