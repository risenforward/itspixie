package com.prisma.singleserver

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.prisma.akkautil.http.ServerExecutor
import com.prisma.api.server.ApiServer
import com.prisma.deploy.server.ClusterServer
import com.prisma.subscriptions.SimpleSubscriptionsServer
import com.prisma.websocket.WebsocketServer
import com.prisma.workers.WorkerServer

object SingleServerMain extends App {
  implicit val system       = ActorSystem("single-server")
  implicit val materializer = ActorMaterializer()

  val port                              = sys.env.getOrElse("PORT", "9000").toInt
  implicit val singleServerDependencies = SingleServerDependencies()

  Version.check()

  ServerExecutor(
    port = port,
    ClusterServer("cluster"),
    WebsocketServer(singleServerDependencies),
    ApiServer(singleServerDependencies.apiSchemaBuilder),
    SimpleSubscriptionsServer(),
    WorkerServer(singleServerDependencies)
  ).startBlocking()
}
