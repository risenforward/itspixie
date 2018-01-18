package com.prisma.deploy.schema.mutations

import com.prisma.deploy.DeployDependencies
import com.prisma.deploy.database.persistence.{MigrationPersistence, ProjectPersistence}
import com.prisma.deploy.migration._
import com.prisma.deploy.migration.inference.{InvalidGCValue, MigrationStepsInferrer, RelationDirectiveNeeded, SchemaInferrer}
import com.prisma.deploy.migration.migrator.Migrator
import com.prisma.deploy.migration.validation.{SchemaError, SchemaSyntaxValidator}
import com.prisma.deploy.schema.InvalidQuery
import com.prisma.messagebus.pubsub.Only
import com.prisma.shared.models.{Function, Migration, MigrationStep, Project, ProjectId, Schema, ServerSideSubscriptionFunction, WebhookDelivery}
import org.scalactic.{Bad, Good, Or}
import play.api.libs.json.JsString
import sangria.parser.QueryParser

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class DeployMutation(
    args: DeployMutationInput,
    project: Project,
    schemaInferrer: SchemaInferrer,
    migrationStepsInferrer: MigrationStepsInferrer,
    schemaMapper: SchemaMapper,
    migrationPersistence: MigrationPersistence,
    projectPersistence: ProjectPersistence,
    migrator: Migrator
)(
    implicit ec: ExecutionContext,
    dependencies: DeployDependencies
) extends Mutation[DeployMutationPayload] {

  val graphQlSdl = QueryParser.parse(args.types) match {
    case Success(res) => res
    case Failure(e)   => throw InvalidQuery(e.getMessage)
  }

  val validator    = SchemaSyntaxValidator(args.types)
  val schemaErrors = validator.validate()

  override def execute: Future[MutationResult[DeployMutationPayload]] = {
    if (schemaErrors.nonEmpty) {
      Future.successful {
        MutationSuccess(
          DeployMutationPayload(
            clientMutationId = args.clientMutationId,
            migration = None,
            errors = schemaErrors
          ))
      }
    } else {
      performDeployment
    }
  }

  private def performDeployment: Future[MutationSuccess[DeployMutationPayload]] = {
    val schemaMapping = schemaMapper.createMapping(graphQlSdl)

    schemaInferrer.infer(project.schema, schemaMapping, graphQlSdl) match {
      case Good(inferredNextSchema) =>
        val steps = migrationStepsInferrer.infer(project.schema, inferredNextSchema, schemaMapping)
        for {
          _         <- handleProjectUpdate()
          functions <- getFunctionModelsOrErrors(args.functions)
          migration <- functions match {
                        case Bad(_)                  => Future.successful(Some(Migration.empty(project.id)))
                        case Good(functionsForInput) => handleMigration(inferredNextSchema, steps, functionsForInput)
                      }
        } yield {
          val functionErrors = functions.swap.getOrElse(Vector.empty)
          MutationSuccess {
            DeployMutationPayload(args.clientMutationId, migration = migration, errors = schemaErrors ++ functionErrors)
          }
        }

      case Bad(err) =>
        Future.successful {
          MutationSuccess(
            DeployMutationPayload(
              clientMutationId = args.clientMutationId,
              migration = None,
              errors = List(err match {
                case RelationDirectiveNeeded(t1, _, t2, _) => SchemaError.global(s"Relation directive required for types $t1 and $t2.")
                case InvalidGCValue(err)                   => SchemaError.global(s"Invalid value '${err.value}' for type ${err.typeIdentifier}.")
              })
            ))
        }
    }
  }

  private def handleProjectUpdate(): Future[_] = {
    if (project.secrets != args.secrets && !args.dryRun.getOrElse(false)) {
      projectPersistence.update(project.copy(secrets = args.secrets))
    } else {
      Future.unit
    }
  }

  def getFunctionModelsOrErrors(fns: Vector[FunctionInput]): Future[Vector[Function] Or Vector[SchemaError]] = {
    validateFunctionInputs(fns).map { errors =>
      if (errors.nonEmpty) {
        Bad(errors)
      } else {
        Good(args.functions.map(convertFunctionInput))
      }
    }
  }

  private def validateFunctionInputs(fns: Vector[FunctionInput]): Future[Vector[SchemaError]] = Future.sequence(fns.map(validateFunctionInput)).map(_.flatten)

  private def validateFunctionInput(fn: FunctionInput): Future[Vector[SchemaError]] = {
    val ProjectId(name, stage) = project.projectId
    dependencies.graphQlClient
      .sendQuery(
        query = s"""{
       |  validateSubscriptionQuery(query: ${JsString(fn.query).toString()}){
       |    errors
       |  }
       |}""".stripMargin,
        path = s"/$name/$stage/private",
        headers = Map("Authorization" -> s"Bearer ${project.secrets.headOption.getOrElse("empty")}")
      )
      .map { response =>
        response.bodyAs[Vector[String]]("data.validateSubscriptionQuery.errors").get
      }
      .map { errorMessages =>
        errorMessages.map(error => SchemaError(`type` = "Subscription", field = fn.name, description = error))
      }
  }

  private def convertFunctionInput(fnInput: FunctionInput): ServerSideSubscriptionFunction = {
    ServerSideSubscriptionFunction(
      name = fnInput.name,
      isActive = true,
      delivery = WebhookDelivery(
        url = fnInput.url,
        headers = fnInput.headers.map(header => header.name -> header.value)
      ),
      query = fnInput.query
    )
  }

  private def handleMigration(nextSchema: Schema, steps: Vector[MigrationStep], functions: Vector[Function]): Future[Option[Migration]] = {
    val migrationNeeded = steps.nonEmpty || functions.nonEmpty
    val isNotDryRun     = !args.dryRun.getOrElse(false)
    if (migrationNeeded && isNotDryRun) {
      invalidateSchema()
      migrator.schedule(project.id, nextSchema, steps, functions).map(Some(_))
    } else {
      Future.successful(None)
    }
  }

  private def invalidateSchema(): Unit = dependencies.invalidationPublisher.publish(Only(project.id), project.id)
}

case class DeployMutationInput(
    clientMutationId: Option[String],
    projectId: String,
    types: String,
    dryRun: Option[Boolean],
    secrets: Vector[String],
    functions: Vector[FunctionInput]
) extends sangria.relay.Mutation

case class FunctionInput(
    name: String,
    query: String,
    url: String,
    headers: Vector[HeaderInput]
)

case class HeaderInput(
    name: String,
    value: String
)

case class DeployMutationPayload(
    clientMutationId: Option[String],
    migration: Option[Migration],
    errors: Seq[SchemaError]
) extends sangria.relay.Mutation
