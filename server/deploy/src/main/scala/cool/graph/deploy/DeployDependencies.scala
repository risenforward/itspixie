package cool.graph.deploy

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cool.graph.deploy.database.persistence.{MigrationPersistenceImpl, ProjectPersistenceImpl}
import cool.graph.deploy.database.schema.InternalDatabaseSchema
import cool.graph.deploy.migration.MigrationApplierImpl
import cool.graph.deploy.migration.migrator.{AsyncMigrator, Migrator}
import cool.graph.deploy.schema.SchemaBuilder
import cool.graph.deploy.seed.InternalDatabaseSeedActions
import cool.graph.deploy.server.{ClusterAuth, ClusterAuthImpl}
import cool.graph.shared.models.Project
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Awaitable, ExecutionContext}
import scala.util.Try

trait DeployDependencies {
  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  import system.dispatcher

  implicit def self: DeployDependencies

  val migrator: Migrator
  val clusterAuth: ClusterAuth

  lazy val internalDb           = setupAndGetInternalDatabase()
  lazy val clientDb             = Database.forConfig("client")
  lazy val projectPersistence   = ProjectPersistenceImpl(internalDb)
  lazy val migrationPersistence = MigrationPersistenceImpl(internalDb)
  lazy val migrationApplier     = MigrationApplierImpl(clientDb)
  lazy val clusterSchemaBuilder = SchemaBuilder()

  def setupAndGetInternalDatabase()(implicit ec: ExecutionContext): MySQLProfile.backend.Database = {
    val rootDb = Database.forConfig(s"internalRoot")
    Await.result(rootDb.run(InternalDatabaseSchema.createSchemaActions(recreate = false)), 30.seconds)
    rootDb.close()

    val db = Database.forConfig("internal")
    await(db.run(InternalDatabaseSeedActions.seedActions()))

    db
  }

  private def await[T](awaitable: Awaitable[T]): T = Await.result(awaitable, Duration.Inf)
}

case class DeployDependenciesImpl()(implicit val system: ActorSystem, val materializer: ActorMaterializer) extends DeployDependencies {
  override implicit def self: DeployDependencies = this

  override val migrator: Migrator = AsyncMigrator(clientDb, migrationPersistence, projectPersistence, migrationApplier)
  override val clusterAuth        = new ClusterAuthImpl(sys.env.get("CLUSTER_PUBLIC_KEY"))
}
