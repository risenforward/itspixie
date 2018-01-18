package com.prisma.api

import com.prisma.api.schema.{ApiUserContext, PrivateSchemaBuilder, SchemaBuilder}
import com.prisma.api.server.{GraphQlQuery, GraphQlRequest}
import com.prisma.shared.models.Project
import com.prisma.util.json.SprayJsonExtensions
import sangria.parser.QueryParser
import sangria.renderer.SchemaRenderer
import sangria.schema.Schema
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.reflect.io.File

case class ApiTestServer()(implicit dependencies: ApiDependencies) extends SprayJsonExtensions with GraphQLResponseAssertions {

  def writeSchemaIntoFile(schema: String): Unit = File("schema").writeAll(schema)

  def printSchema: Boolean = false
  def writeSchemaToFile    = false
  def logSimple: Boolean   = false

  /**
    * Execute a Query that must succeed.
    */
  def querySimple(query: String)(implicit project: Project): JsValue                       = executeQuerySimple(query, project)
  def querySimple(query: String, dataContains: String)(implicit project: Project): JsValue = executeQuerySimple(query, project, dataContains)

  def executeQuerySimple(
      query: String,
      project: Project,
      dataContains: String = "",
      variables: JsValue = JsObject.empty,
      requestId: String = "CombinedTestDatabase.requestId"
  ): JsValue = {
    val result = executeQuerySimpleWithAuthentication(
      query = query,
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
  def querySimpleThatMustFail(query: String, errorCode: Int)(implicit project: Project): JsValue = executeQuerySimpleThatMustFail(query, project, errorCode)
  def querySimpleThatMustFail(query: String, errorCode: Int, errorCount: Int)(implicit project: Project): JsValue =
    executeQuerySimpleThatMustFail(query = query, project = project, errorCode = errorCode, errorCount = errorCount)
  def querySimpleThatMustFail(query: String, errorCode: Int, errorContains: String)(implicit project: Project): JsValue =
    executeQuerySimpleThatMustFail(query = query, project = project, errorCode = errorCode, errorContains = errorContains)
  def querySimpleThatMustFail(query: String, errorCode: Int, errorContains: String, errorCount: Int)(implicit project: Project): JsValue =
    executeQuerySimpleThatMustFail(query = query, project = project, errorCode = errorCode, errorCount = errorCount, errorContains = errorContains)

  def executeQuerySimpleThatMustFail(query: String, project: Project, userId: String, errorCode: Int): JsValue =
    executeQuerySimpleThatMustFail(query = query, project = project, userId = Some(userId), errorCode = errorCode)
  def executeQuerySimpleThatMustFail(query: String, project: Project, userId: String, errorCode: Int, errorCount: Int): JsValue =
    executeQuerySimpleThatMustFail(query = query, project = project, userId = Some(userId), errorCode = errorCode, errorCount = errorCount)
  def executeQuerySimpleThatMustFail(query: String, project: Project, errorCode: Int, errorContains: String, userId: String): JsValue =
    executeQuerySimpleThatMustFail(query = query, project = project, userId = Some(userId), errorCode = errorCode, errorContains = errorContains)
  def executeQuerySimpleThatMustFail(query: String, project: Project, userId: String, errorCode: Int, errorCount: Int, errorContains: String): JsValue =
    executeQuerySimpleThatMustFail(query = query,
                                   project = project,
                                   userId = Some(userId),
                                   errorCode = errorCode,
                                   errorCount = errorCount,
                                   errorContains = errorContains)

  def executeQuerySimpleThatMustFail(query: String,
                                     project: Project,
                                     errorCode: Int,
                                     errorCount: Int = 1,
                                     errorContains: String = "",
                                     userId: Option[String] = None,
                                     variables: JsValue = JsObject(),
                                     requestId: String = "CombinedTestDatabase.requestId",
                                     graphcoolHeader: Option[String] = None): JsValue = {
    val result = executeQuerySimpleWithAuthentication(
      query = query,
      project = project,
      variables = variables,
      requestId = requestId,
      graphcoolHeader = graphcoolHeader
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
      variables: JsValue = JsObject(),
      requestId: String = "CombinedTestDatabase.requestId",
      graphcoolHeader: Option[String] = None
  ): JsValue = {
    val schemaBuilder = SchemaBuilder()(dependencies.system, dependencies)
    querySchema(
      query = query,
      project = project,
      schema = schemaBuilder(project),
      variables = variables,
      requestId = requestId,
      graphcoolHeader = graphcoolHeader
    )
  }

  def queryPrivateSchema(query: String, project: Project): JsValue = {
    val schemaBuilder = PrivateSchemaBuilder(project)(dependencies, dependencies.system)
    querySchema(
      query = query,
      project = project,
      schema = schemaBuilder.build(),
      variables = JsObject.empty,
      requestId = "private-api-request",
      graphcoolHeader = None
    )
  }

  private def querySchema(
      query: String,
      project: Project,
      schema: Schema[ApiUserContext, Unit],
      variables: JsValue,
      requestId: String,
      graphcoolHeader: Option[String]
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
      sourceHeader = graphcoolHeader,
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
