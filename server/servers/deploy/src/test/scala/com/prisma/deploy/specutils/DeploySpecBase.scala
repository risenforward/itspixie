package com.prisma.deploy.specutils

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.prisma.ConnectorAwareTest
import com.prisma.shared.models.{Migration, Project, ProjectId}
import com.prisma.utils.await.AwaitUtils
import com.prisma.utils.json.PlayJsonExtensions
import cool.graph.cuid.Cuid
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import play.api.libs.json.JsString

import scala.collection.mutable.ArrayBuffer

trait DeploySpecBase extends ConnectorAwareTest with BeforeAndAfterEach with BeforeAndAfterAll with AwaitUtils with PlayJsonExtensions { self: Suite =>

  implicit lazy val system                                   = ActorSystem()
  implicit lazy val materializer                             = ActorMaterializer()
  implicit lazy val testDependencies: DeployTestDependencies = DeployTestDependencies()

  override def prismaConfig = testDependencies.config

  val server            = DeployTestServer()
  val internalDB        = testDependencies.deployConnector
  val projectsToCleanUp = new ArrayBuffer[String]

  val basicTypesGql =
    """
      |type TestModel {
      |  id: ID! @unique
      |}
    """.stripMargin

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    testDependencies.deployConnector.initialize().await()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
//    projectsToCleanUp.foreach(internalDB.deleteProjectDatabase)
    testDependencies.deployConnector.shutdown().await()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
//    projectsToCleanUp.foreach(internalDB.deleteProjectDatabase)
//    projectsToCleanUp.clear()
    testDependencies.deployConnector.reset().await
  }

  def setupProject(
      schema: String,
      name: String = Cuid.createCuid(),
      stage: String = Cuid.createCuid(),
      secrets: Vector[String] = Vector.empty
  ): (Project, Migration) = {

    val projectId = testDependencies.projectIdEncoder.fromSegments(List(name, stage))
    projectsToCleanUp :+ projectId
    server.addProject(name, stage)
    server.deploySchema(name, stage, schema.stripMargin, secrets)
  }

  def formatSchema(schema: String): String = JsString(schema).toString()
  def escapeString(str: String): String    = JsString(str).toString()
}
