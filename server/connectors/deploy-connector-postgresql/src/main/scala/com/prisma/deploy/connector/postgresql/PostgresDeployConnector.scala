package com.prisma.deploy.connector.postgresql

import com.prisma.config.DatabaseConfig
import com.prisma.deploy.connector._
import com.prisma.deploy.connector.postgresql.database.{InternalDatabaseSchema, PostgresDeployDatabaseMutationBuilder, TelemetryTable}
import com.prisma.deploy.connector.postgresql.impls._
import com.prisma.metrics.PrismaCloudSecretLoader
import com.prisma.shared.models.{Project, ProjectIdEncoder}
import org.joda.time.DateTime
import slick.dbio.Effect.Read
import slick.dbio.{DBIOAction, NoStream}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

case class PostgresDeployConnector(
    dbConfig: DatabaseConfig,
    isActive: Boolean
)(implicit ec: ExecutionContext)
    extends DeployConnector {
  lazy val internalDatabaseDefs = PostgresInternalDatabaseDefs(dbConfig)
  lazy val internalDatabaseRoot = internalDatabaseDefs.internalDatabaseRoot // DB prisma, schema public
  lazy val internalDatabase     = internalDatabaseDefs.internalDatabase // DB prisma, schema management

  override lazy val projectPersistence: ProjectPersistence           = ProjectPersistenceImpl(internalDatabase)
  override lazy val migrationPersistence: MigrationPersistence       = MigrationPersistenceImpl(internalDatabase)
  override lazy val deployMutactionExecutor: DeployMutactionExecutor = PostgresDeployMutactionExecutor(internalDatabaseRoot)

  override def createProjectDatabase(id: String): Future[Unit] = {
    val action = PostgresDeployDatabaseMutationBuilder.createClientDatabaseForProject(projectId = id)
    internalDatabaseRoot.run(action)
  }

  override def deleteProjectDatabase(id: String): Future[Unit] = {
    val action = PostgresDeployDatabaseMutationBuilder.deleteProjectDatabase(projectId = id).map(_ => ())
    internalDatabaseRoot.run(action)
  }

  override def getAllDatabaseSizes(): Future[Vector[DatabaseSize]] = {
    val action = {
      val query = sql"""
           SELECT table_schema, sum( data_length + index_length) / 1024 / 1024 FROM information_schema.TABLES GROUP BY table_schema
         """
      query.as[(String, Double)].map { tuples =>
        tuples.map { tuple =>
          DatabaseSize(tuple._1, tuple._2)
        }
      }
    }

    internalDatabaseRoot.run(action)
  }

  override def clientDBQueries(project: Project): ClientDbQueries      = PostgresClientDbQueries(project, internalDatabaseRoot)
  override def getOrCreateTelemetryInfo(): Future[TelemetryInfo]       = internalDatabase.run(TelemetryTable.getOrCreateInfo())
  override def updateTelemetryInfo(lastPinged: DateTime): Future[Unit] = internalDatabase.run(TelemetryTable.updateInfo(lastPinged)).map(_ => ())
  override def projectIdEncoder: ProjectIdEncoder                      = ProjectIdEncoder('$')
  override def cloudSecretPersistence: CloudSecretPersistence          = CloudSecretPersistenceImpl(internalDatabase)

  override def initialize(): Future[Unit] = {
    // We're ignoring failures for createDatabaseAction as there is no "create if not exists" in psql
    internalDatabaseDefs.setupDatabase
      .run(InternalDatabaseSchema.createDatabaseAction(internalDatabaseDefs.dbName))
      .transformWith { _ =>
        val action = InternalDatabaseSchema.createSchemaActions(internalDatabaseDefs.managementSchemaName, recreate = false)
        internalDatabaseRoot.run(action)
      }
      .flatMap(_ => internalDatabaseDefs.setupDatabase.shutdown)
  }

  override def reset(): Future[Unit] = truncateManagementTablesInDatabase(internalDatabase)

  override def shutdown() = {
    for {
      _ <- internalDatabaseRoot.shutdown
      _ <- internalDatabase.shutdown
    } yield ()
  }

  override def databaseIntrospectionInferrer(projectId: String): DatabaseIntrospectionInferrer = {
    if (isActive) {
      EmptyDatabaseIntrospectionInferrer
    } else {
      val schema = dbConfig.schema.getOrElse(projectId)
      DatabaseIntrospectionInferrerImpl(internalDatabaseRoot, schema)
    }
  }

  protected def truncateManagementTablesInDatabase(database: Database)(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      schemas <- database.run(getTables())
      _       <- database.run(dangerouslyTruncateTables(schemas))
    } yield ()
  }

  private def getTables()(implicit ec: ExecutionContext): DBIOAction[Vector[String], NoStream, Read] = {
    sql"""SELECT table_name
          FROM information_schema.tables
          WHERE table_schema = '#${internalDatabaseDefs.managementSchemaName}'
          AND table_type = 'BASE TABLE';""".as[String]
  }

  private def dangerouslyTruncateTables(tableNames: Vector[String]): DBIOAction[Unit, NoStream, Effect] = {
    DBIO.seq(tableNames.map(name => sqlu"""TRUNCATE TABLE "#$name" cascade"""): _*)
  }
}
