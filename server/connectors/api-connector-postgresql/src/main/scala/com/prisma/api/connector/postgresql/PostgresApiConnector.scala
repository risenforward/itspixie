package com.prisma.api.connector.postgresql

import com.prisma.api.connector.{ApiConnector, NodeQueryCapability}
import com.prisma.api.connector.postgresql.database.{Databases, PostgresDataResolver}
import com.prisma.api.connector.postgresql.impl.PostgresDatabaseMutactionExecutor
import com.prisma.config.DatabaseConfig
import com.prisma.shared.models.{Project, ProjectIdEncoder}

import scala.concurrent.{ExecutionContext, Future}

case class PostgresApiConnector(config: DatabaseConfig, createRelayIds: Boolean)(implicit ec: ExecutionContext) extends ApiConnector {
  lazy val databases = Databases.initialize(config)

  override def initialize() = {
    databases
    Future.unit
  }

  override def shutdown() = {
    for {
      _ <- databases.master.shutdown
      _ <- databases.readOnly.shutdown
    } yield ()
  }

  override val databaseMutactionExecutor: PostgresDatabaseMutactionExecutor = PostgresDatabaseMutactionExecutor(databases.master, createRelayIds = true)
  override def dataResolver(project: Project)                               = PostgresDataResolver(project, databases.readOnly, schemaName = None)
  override def masterDataResolver(project: Project)                         = PostgresDataResolver(project, databases.master, schemaName = None)
  override def projectIdEncoder: ProjectIdEncoder                           = ProjectIdEncoder('$')
  override def capabilities                                                 = Vector(NodeQueryCapability)
}
