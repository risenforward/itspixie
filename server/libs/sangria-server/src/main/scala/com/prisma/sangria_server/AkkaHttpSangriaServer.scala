package com.prisma.sangria_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, HttpRequest, RemoteAddress}
import akka.http.scaladsl.server.Directives.{as, entity, extractClientIP, _}
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.server.{ExceptionHandler, Route, UnsupportedWebSocketSubprotocolRejection}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.concurrent.{Await, Future}

object AkkaHttpSangriaServer extends SangriaServerExecutor {
  override def create(handler: SangriaHandler, port: Int, requestPrefix: String) = AkkaHttpSangriaServer(handler, port, requestPrefix)
}

case class AkkaHttpSangriaServer(handler: SangriaHandler, port: Int, requestPrefix: String) extends SangriaServer with PlayJsonSupport {
  import scala.concurrent.duration._

  implicit val system       = ActorSystem("sangria-server")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val routes = {
    extractRequest { request =>
      val requestId = createRequestId()
      handleExceptions(toplevelExceptionHandler(requestId)) {
        extractClientIP { clientIp =>
          post {
            entity(as[JsValue]) { requestJson =>
              val rawRequest = akkaRequestToRawRequest(request, requestJson, clientIp, requestId)
              complete(OK -> handler.handleRawRequest(rawRequest))
            }
          } ~ (get & path("status")) {
            complete("OK")
          } ~ get {
            extractUpgradeToWebSocket { upgrade =>
              upgrade.requestedProtocols.headOption match {
                case Some(protocol) if handler.supportedWebsocketProtocols.contains(protocol) =>
                  val originalFlow = handler.newWebsocketSession(akkaRequestToRawWebsocketRequest(request, clientIp, protocol, requestId))
                  val akkaHttpFlow = Flow[Message].map(akkaWebSocketMessageToModel).via(originalFlow).map(modelToAkkaWebsocketMessage)
                  handleWebSocketMessagesForProtocol(akkaHttpFlow, protocol)
                case _ =>
                  reject(UnsupportedWebSocketSubprotocolRejection(handler.supportedWebsocketProtocols.head))
              }
            } ~
              getFromResource("graphiql.html", ContentTypes.`text/html(UTF-8)`)
          }
        }
      }
    }
  }

  def toplevelExceptionHandler(requestId: String) = ExceptionHandler {
    case e: Throwable =>
      println(e.getMessage)
      e.printStackTrace()
      complete(InternalServerError -> JsonErrorHelper.errorJson(requestId, e.getMessage))
  }

  private def akkaRequestToRawRequest(req: HttpRequest, json: JsValue, ip: RemoteAddress, requestId: String): RawRequest = {
    val reqMethod = req.method match {
      case HttpMethods.GET  => HttpMethod.Get
      case HttpMethods.POST => HttpMethod.Post
      case _                => sys.error("not allowed")
    }
    val headers = req.headers.map(h => h.name -> h.value).toMap
    val path    = req.uri.path.toString.split('/').filter(_.nonEmpty)
    RawRequest(
      id = requestId,
      method = reqMethod,
      path = path.toVector,
      headers = headers,
      json = json,
      ip = ip.toString
    )
  }

  private def akkaRequestToRawWebsocketRequest(req: HttpRequest, ip: RemoteAddress, protocol: String, requestId: String): RawWebsocketRequest = {
    val headers = req.headers.map(h => h.name -> h.value).toMap
    val path    = req.uri.path.toString.split('/')
    RawWebsocketRequest(
      id = requestId,
      path = path.toVector,
      headers = headers,
      ip = ip.toString,
      protocol = protocol
    )
  }

  private def modelToAkkaWebsocketMessage(message: WebSocketMessage): Message = TextMessage(message.body)
  private def akkaWebSocketMessageToModel(message: Message) = {
    message match {
      case TextMessage.Strict(body) => WebSocketMessage(body)
      case x                        => sys.error(s"Not supported: $x")
    }
  }

  lazy val serverBinding: Future[ServerBinding] = {
    val binding = Http().bindAndHandle(Route.handlerFlow(routes), "0.0.0.0", port)
    binding.foreach(b => println(s"Server running on :${b.localAddress.getPort}"))
    binding
  }

  def start: Future[Unit] = serverBinding.map(_ => ())
  def stop: Future[Unit]  = serverBinding.map(_.unbind)

  // Starts the server and blocks the calling thread until the underlying actor system terminates.
  def startBlocking: Unit = {
    start
    Await.result(system.whenTerminated, Duration.Inf)
  }

  def stopBlocking(duration: Duration = 15.seconds): Unit = Await.result(stop, duration)
}

object JsonErrorHelper {

  def errorJson(requestId: String, message: String, errorCode: Int): JsObject = errorJson(requestId, message, Some(errorCode))
  def errorJson(requestId: String, message: String, errorCode: Option[Int] = None): JsObject = errorCode match {
    case None       => Json.obj("errors" -> Seq(Json.obj("message" -> message, "requestId" -> requestId)))
    case Some(code) => Json.obj("errors" -> Seq(Json.obj("message" -> message, "code"      -> code, "requestId" -> requestId)))
  }
}
