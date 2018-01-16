package com.prisma.deploy.specutils

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cool.graph.cuid.Cuid
import com.prisma.shared.models.{Migration, MigrationId, Project}
import com.prisma.utils.await.AwaitUtils
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import spray.json.JsString

import scala.collection.mutable.ArrayBuffer

trait DeploySpecBase extends BeforeAndAfterEach with BeforeAndAfterAll with AwaitUtils with SprayJsonExtensions { self: Suite =>

  implicit lazy val system                                   = ActorSystem()
  implicit lazy val materializer                             = ActorMaterializer()
  implicit lazy val testDependencies: DeployTestDependencies = DeployTestDependencies()

  val server            = DeployTestServer()
  val internalDb        = testDependencies.internalTestDb
  val clientDb          = testDependencies.clientTestDb
  val projectsToCleanUp = new ArrayBuffer[String]

  val basicTypesGql =
    """
      |type TestModel {
      |  id: ID! @unique
      |}
    """.stripMargin.trim()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    internalDb.createInternalDatabaseSchema()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    internalDb.shutdown()
    clientDb.shutdown()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    internalDb.truncateTables()
    projectsToCleanUp.foreach(clientDb.delete)
    projectsToCleanUp.clear()
  }

  def setupProject(schema: String, name: String = Cuid.createCuid(), stage: String = Cuid.createCuid()): (Project, Migration) = {
    server.query(s"""
        |mutation {
        | addProject(input: {
        |   name: "$name",
        |   stage: "$stage"
        | }) {
        |   project {
        |     name
        |     stage
        |   }
        | }
        |}
      """.stripMargin)

    val projectId = name + "@" + stage
    projectsToCleanUp :+ projectId

    val deployResult = server.query(s"""
        |mutation {
        |  deploy(input:{name: "$name", stage: "$stage", types: ${formatSchema(schema)}}){
        |    migration {
        |      revision
        |    }
        |    errors {
        |      description
        |    }
        |  }
        |}
      """.stripMargin)

    val revision = deployResult.pathAsLong("data.deploy.migration.revision")

    (
      testDependencies.projectPersistence.load(projectId).await.get,
      testDependencies.migrationPersistence.byId(MigrationId(projectId, revision.toInt)).await.get
    )
  }

  def formatSchema(schema: String): String = JsString(schema).toString()
  def escapeString(str: String): String    = JsString(str).toString()
}
