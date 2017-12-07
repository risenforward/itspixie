package cool.graph.api

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cool.graph.api.database.{DataResolver, DatabaseMutationBuilder, DatabaseQueryBuilder}
import cool.graph.deploy.migration.mutactions.{ClientSqlMutaction, CreateRelationTable}
import cool.graph.shared.models._
import cool.graph.utils.await.AwaitUtils
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.MySQLProfile.backend.DatabaseDef

case class ApiTestDatabase()(implicit dependencies: ApiDependencies) extends AwaitUtils {

  implicit lazy val system: ActorSystem             = dependencies.system
  implicit lazy val materializer: ActorMaterializer = dependencies.materializer
  private lazy val clientDatabase: DatabaseDef      = dependencies.databases.master

  def setupProject(project: Project): Unit = {
    val databaseOperations = TestDatabaseOperations(clientDatabase)
    databaseOperations.deleteProjectDatabase(project)
    databaseOperations.createProjectDatabase(project)

    // The order here is very important or foreign key constraints will fail
    project.models.foreach(databaseOperations.createModelTable(project, _))
    project.relations.foreach(databaseOperations.createRelationTable(project, _))
  }

  def truncateProjectDatabase(project: Project): Unit = {
    val tables = clientDatabase.run(DatabaseQueryBuilder.getTables(project.id)).await
    val dbAction = {
      val actions = List(sqlu"""USE `#${project.id}`;""") ++ List(DatabaseMutationBuilder.dangerouslyTruncateTable(tables))
      DBIO.seq(actions: _*)
    }
    clientDatabase.run(dbAction).await()
  }
}

case class TestDatabaseOperations(
    clientDatabase: DatabaseDef
) extends AwaitUtils {

  def createProjectDatabase(project: Project): Unit                   = runDbActionOnClientDb(DatabaseMutationBuilder.createClientDatabaseForProject(project.id))
  def createModelTable(project: Project, model: Model): Unit          = runDbActionOnClientDb(DatabaseMutationBuilder.createTableForModel(project.id, model))
  def createRelationTable(project: Project, relation: Relation): Unit = runMutaction(CreateRelationTable(project = project, relation = relation))

  def deleteProjectDatabase(project: Project): Unit = dropDatabases(Vector(project.id))

  //  def loadRelationFieldMirrors(project: Project, relation: Relation): Unit = {
//    relation.fieldMirrors.foreach { mirror =>
//      runMutaction(CreateRelationFieldMirrorColumn(project, relation, project.getFieldById_!(mirror.fieldId)))
//    }
//  }

  def deleteExistingDatabases(): Unit = {
    val schemas = {
      clientDatabase
        .run(DatabaseQueryBuilder.getSchemas)
        .await
        .filter(db => !Vector("information_schema", "mysql", "performance_schema", "sys", "innodb", "graphcool").contains(db))
    }
    dropDatabases(schemas)
  }

  private def dropDatabases(dbs: Vector[String]): Unit = {
    val dbAction = DBIO.seq(dbs.map(db => DatabaseMutationBuilder.dropDatabaseIfExists(database = db)): _*)
    clientDatabase.run(dbAction).await(60)
  }

  private def runMutaction(mutaction: ClientSqlMutaction): Unit                         = runDbActionOnClientDb(mutaction.execute.await().sqlAction)
  private def runDbActionOnClientDb(action: DBIOAction[Any, NoStream, Effect.All]): Any = clientDatabase.run(action).await()
}
