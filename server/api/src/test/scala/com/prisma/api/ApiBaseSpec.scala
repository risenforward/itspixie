package com.prisma.api

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.prisma.api.database.DataResolver
import com.prisma.api.util.StringMatchers
import com.prisma.shared.models.Project
import com.prisma.util.json.SprayJsonExtensions
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import play.api.libs.json.JsString

trait ApiBaseSpec extends BeforeAndAfterEach with BeforeAndAfterAll with SprayJsonExtensions with StringMatchers { self: Suite =>

  implicit lazy val system           = ActorSystem()
  implicit lazy val materializer     = ActorMaterializer()
  implicit lazy val testDependencies = new ApiDependenciesForTest
  val server                         = ApiTestServer()
  val database                       = ApiTestDatabase()

  def dataResolver(project: Project): DataResolver = DataResolver(project = project)

  override protected def afterAll(): Unit = {
    super.afterAll()
    testDependencies.destroy
  }

  def escapeString(str: String) = JsString(str).toString()
}
