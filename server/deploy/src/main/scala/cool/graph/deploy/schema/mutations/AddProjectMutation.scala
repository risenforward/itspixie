package cool.graph.deploy.schema.mutations

import cool.graph.cuid.Cuid
import cool.graph.deploy.database.persistence.ProjectPersistence
import cool.graph.deploy.migration.mutactions.CreateClientDatabaseForProject
import cool.graph.shared.models._
import cool.graph.shared.project_dsl.TestProject
import slick.jdbc.MySQLProfile.backend.DatabaseDef
import scala.concurrent.{ExecutionContext, Future}

case class AddProjectMutation(
    args: AddProjectInput,
    client: Client,
    projectPersistence: ProjectPersistence,
    clientDb: DatabaseDef
)(
    implicit ec: ExecutionContext
) extends Mutation[AddProjectMutationPayload] {

  override def execute: Future[MutationResult[AddProjectMutationPayload]] = {
    val newProject = Project(
      id = Cuid.createCuid(),
      name = args.name,
      alias = args.alias,
      projectDatabase = TestProject.database,
      ownerId = client.id
    )

    val migration = Migration(
      projectId = newProject.id,
      revision = 0,
      hasBeenApplied = true,
      steps = Vector(SetupProject())
    )

    for {
      _    <- projectPersistence.save(newProject)
      stmt <- CreateClientDatabaseForProject(newProject.id).execute
      _    <- clientDb.run(stmt.sqlAction)
      _    <- projectPersistence.save(newProject, migration)
    } yield MutationSuccess(AddProjectMutationPayload(args.clientMutationId, newProject))
  }
}

case class AddProjectMutationPayload(
    clientMutationId: Option[String],
    project: Project
) extends sangria.relay.Mutation

case class AddProjectInput(
    clientMutationId: Option[String],
    name: String,
    alias: Option[String]
) extends sangria.relay.Mutation
