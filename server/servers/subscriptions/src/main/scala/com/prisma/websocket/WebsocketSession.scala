package com.prisma.websocket

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, PoisonPill, ReceiveTimeout, Stash, Terminated}
import akka.http.scaladsl.model.ws.TextMessage
import com.prisma.akkautil.{LogUnhandled, LogUnhandledExceptions}
import com.prisma.errors.ErrorReporter
import com.prisma.messagebus.QueuePublisher
import com.prisma.subscriptions.SubscriptionDependencies
import com.prisma.subscriptions.protocol.StringOrInt
import com.prisma.subscriptions.protocol.SubscriptionProtocolV05.Requests.SubscriptionSessionRequestV05
import com.prisma.subscriptions.protocol.SubscriptionProtocolV07.Requests.{GqlStop, SubscriptionSessionRequest}
import com.prisma.subscriptions.protocol.SubscriptionProtocolV07.Responses.GqlError
import com.prisma.subscriptions.protocol.SubscriptionSessionManager.Requests.{EnrichedSubscriptionRequest, EnrichedSubscriptionRequestV05, StopSession}
import com.prisma.subscriptions.util.PlayJson
import com.prisma.websocket.protocol.Request
import play.api.libs.json.{JsError, JsSuccess, Json}

import scala.collection.mutable
import scala.concurrent.duration._ // if you don't supply your own Protocol (see below)

object WebsocketSessionManager {
  object Requests {
    case class OpenWebsocketSession(projectId: String, sessionId: String, outgoing: ActorRef)
    case class CloseWebsocketSession(sessionId: String)

//    case class IncomingWebsocketMessage(projectId: String, sessionId: String, body: String)
    case class IncomingQueueMessage(sessionId: String, body: String)

    case class RegisterWebsocketSession(sessionId: String, actor: ActorRef)
  }

  object Responses {
    case class OutgoingMessage(text: String)
  }
}

case class WebsocketSessionManager(
    requestsPublisher: QueuePublisher[Request],
)(implicit val reporter: ErrorReporter)
    extends Actor
    with LogUnhandled
    with LogUnhandledExceptions {
  import WebsocketSessionManager.Requests._

  val websocketSessions = mutable.Map.empty[String, ActorRef]

  override def receive: Receive = logUnhandled {
//    case OpenWebsocketSession(projectId, sessionId, outgoing) =>
//      val ref = context.actorOf(Props(WebsocketSession(projectId, sessionId, outgoing, requestsPublisher, bugsnag)))
//      context.watch(ref)
//      websocketSessions += sessionId -> ref
//
//    case CloseWebsocketSession(sessionId) =>
//      websocketSessions.get(sessionId).foreach(context.stop)

//    case req: IncomingWebsocketMessage =>
//      websocketSessions.get(req.sessionId) match {
//        case Some(session) => session ! req
//        case None          => println(s"No session actor found for ${req.sessionId} when processing websocket message. This should only happen very rarely.")
//      }

    case req: RegisterWebsocketSession =>
      context.watch(req.actor)
      websocketSessions += req.sessionId -> req.actor

    case req: IncomingQueueMessage =>
      websocketSessions.get(req.sessionId) match {
        case Some(session) => session ! req
        case None          => println(s"No session actor found for ${req.sessionId} when processing queue message. This should only happen very rarely.")
      }

    case Terminated(terminatedActor) =>
      websocketSessions.retain {
        case (_, sessionActor) => sessionActor != terminatedActor
      }
  }
}

case class WebsocketSession(
    projectId: String,
    sessionId: String,
    outgoing: ActorRef,
    manager: ActorRef,
    subscriptionSessionManager: ActorRef,
    isV7protocol: Boolean
)(implicit dependencies: SubscriptionDependencies)
    extends Actor
    with LogUnhandled
    with LogUnhandledExceptions
    with Stash {
  import WebsocketSessionManager.Requests._
  import metrics.SubscriptionWebsocketMetrics._

  val reporter    = dependencies.reporter
  implicit val ec = context.system.dispatcher

  activeWsConnections.inc
  context.setReceiveTimeout(FiniteDuration(10, TimeUnit.MINUTES))

  manager ! RegisterWebsocketSession(sessionId, self)

  context.system.scheduler.schedule(
    dependencies.keepAliveIntervalSeconds.seconds,
    dependencies.keepAliveIntervalSeconds.seconds,
    outgoing,
    if (isV7protocol) {
      TextMessage.Strict("""{"type":"ka"}""")
    } else {
      TextMessage.Strict("""{"type":"keepalive"}""")
    }
  )

  def receive: Receive = logUnhandled {
    case TextMessage.Strict(body) =>
      import com.prisma.subscriptions.protocol.ProtocolV07.SubscriptionRequestReaders._
      import com.prisma.subscriptions.protocol.ProtocolV07.SubscriptionResponseWriters._
      import com.prisma.subscriptions.protocol.ProtocolV05.SubscriptionRequestReaders._

      val msg = if (isV7protocol) {
        for {
          req <- PlayJson.parse(body).flatMap(_.validate[SubscriptionSessionRequest])
        } yield EnrichedSubscriptionRequest(sessionId = sessionId, projectId = projectId, req)
      } else {
        for {
          req <- PlayJson.parse(body).flatMap(_.validate[SubscriptionSessionRequestV05])
        } yield EnrichedSubscriptionRequestV05(sessionId = sessionId, projectId = projectId, req)
      }
      msg match {
        case JsSuccess(m, _) => subscriptionSessionManager ! m
        case JsError(_)      => outgoing ! TextMessage(Json.toJson(GqlError(StringOrInt(string = Some(""), int = None), "The message can't be parsed")).toString())
      }
      incomingWebsocketMessageCount.inc()

    case IncomingQueueMessage(_, body) =>
      outgoing ! TextMessage(body)
      outgoingWebsocketMessageCount.inc()

    case ReceiveTimeout =>
      context.stop(self)
  }

  override def postStop = {
    activeWsConnections.dec
    outgoing ! PoisonPill
    subscriptionSessionManager ! StopSession(sessionId)
  }
}
