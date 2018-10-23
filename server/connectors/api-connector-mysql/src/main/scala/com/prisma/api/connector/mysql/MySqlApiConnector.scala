package com.prisma.api.connector.mysql

import com.prisma.api.connector.jdbc.impl.{JdbcDataResolver, JdbcDatabaseMutactionExecutor}
import com.prisma.api.connector.{ApiConnector, DatabaseMutactionExecutor}
import com.prisma.config.DatabaseConfig
import com.prisma.shared.models.ApiConnectorCapability._
import com.prisma.shared.models.{ConnectorCapability, Project, ProjectIdEncoder}

import scala.concurrent.{ExecutionContext, Future}

case class MySqlApiConnector(config: DatabaseConfig)(implicit ec: ExecutionContext) extends ApiConnector {
  lazy val databases = MySqlDatabasesFactory.initialize(config)

  override def initialize() = {
    databases
    Future.unit
  }

  override def shutdown() = {
    for {
      _ <- databases.primary.database.shutdown
      _ <- databases.replica.database.shutdown
    } yield ()
  }

  override def databaseMutactionExecutor: DatabaseMutactionExecutor = JdbcDatabaseMutactionExecutor(databases.primary, isActive = true, schemaName = None)
  override def dataResolver(project: Project)                       = JdbcDataResolver(project, databases.replica, schemaName = None)(ec)
  override def masterDataResolver(project: Project)                 = JdbcDataResolver(project, databases.primary, schemaName = None)(ec)

  override def projectIdEncoder: ProjectIdEncoder = ProjectIdEncoder('@')

  override val capabilities: Set[ConnectorCapability] = {
    val isActive = true
    val common   = Set(TransactionalExecutionCapability, JoinRelationsCapability)
    if (isActive) {
      Set(NodeQueryCapability, ImportExportCapability, NonEmbeddedScalarListCapability) ++ common
    } else {
      common
    }
  }

}
