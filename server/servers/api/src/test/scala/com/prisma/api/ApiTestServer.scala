package com.prisma.api

import com.prisma.api.schema.{ApiUserContext, PrivateSchemaBuilder, SchemaBuilder}
import com.prisma.api.server.{GraphQlQuery, GraphQlRequest}
import com.prisma.shared.models.Project
import com.prisma.utils.json.PlayJsonExtensions
import play.api.libs.json._
import sangria.parser.QueryParser
import sangria.renderer.SchemaRenderer
import sangria.schema.Schema

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.reflect.io.File

case class ApiTestServer()(implicit dependencies: ApiDependencies) extends PlayJsonExtensions {

  def writeSchemaIntoFile(schema: String): Unit = File("schema").writeAll(schema)

  def printSchema: Boolean = false
  def writeSchemaToFile    = false
  def logSimple: Boolean   = false

  /**
    * Execute a Query that must succeed.
    */
  def query(
      query: String,
      project: Project,
      dataContains: String = "",
      variables: JsValue = JsObject.empty,
      requestId: String = "CombinedTestDatabase.requestId"
  ): JsValue = {
    val result = executeQuerySimpleWithAuthentication(
      query = query.stripMargin,
      project = project,
      variables = variables,
      requestId = requestId
    )

    result.assertSuccessfulResponse(dataContains)
    result
  }

  /**
    * Execute a Query that must fail.
    */
  def queryThatMustFail(query: String,
                        project: Project,
                        errorCode: Int,
                        errorCount: Int = 1,
                        errorContains: String = "",
                        userId: Option[String] = None,
                        variables: JsValue = JsObject.empty,
                        requestId: String = "CombinedTestDatabase.requestId",
                        prismaHeader: Option[String] = None): JsValue = {
    val result = executeQuerySimpleWithAuthentication(
      query = query,
      project = project,
      variables = variables,
      requestId = requestId
    )
    result.assertFailingResponse(errorCode, errorCount, errorContains)
    result
  }

  /**
    * Execute a Query without Checks.
    */
  def executeQuerySimpleWithAuthentication(
      query: String,
      project: Project,
      variables: JsValue = JsObject.empty,
      requestId: String = "CombinedTestDatabase.requestId"
  ): JsValue = {
    val schemaBuilder = SchemaBuilder()(dependencies.system, dependencies)
    querySchema(
      query = query,
      project = project,
      schema = schemaBuilder(project),
      variables = variables,
      requestId = requestId,
    )
  }

  def queryPrivateSchema(query: String, project: Project, variables: JsObject = JsObject.empty): JsValue = {
    val schemaBuilder = PrivateSchemaBuilder(project)(dependencies, dependencies.system)
    querySchema(
      query = query,
      project = project,
      schema = schemaBuilder.build(),
      variables = variables,
      requestId = "private-api-request"
    )
  }

  private def querySchema(
      query: String,
      project: Project,
      schema: Schema[ApiUserContext, Unit],
      variables: JsValue,
      requestId: String,
  ): JsValue = {
    val queryAst = QueryParser.parse(query).get

    lazy val renderedSchema = SchemaRenderer.renderSchema(schema)
    if (printSchema) println(renderedSchema)
    if (writeSchemaToFile) writeSchemaIntoFile(renderedSchema)

    val graphqlQuery = GraphQlQuery(query = queryAst, operationName = None, variables = variables, queryString = query)
    val graphQlRequest = GraphQlRequest(
      id = requestId,
      ip = "test.ip",
      json = JsObject.empty,
      project = project,
      schema = schema,
      queries = Vector(graphqlQuery),
      isBatch = false
    )

    val result = Await.result(dependencies.graphQlRequestHandler.handle(graphQlRequest), Duration.Inf)._2

    println("Request Result: " + result)
    result
  }
}
