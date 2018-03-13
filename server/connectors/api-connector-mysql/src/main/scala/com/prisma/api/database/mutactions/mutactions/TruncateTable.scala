package com.prisma.api.database.mutactions.mutactions

import com.prisma.api.database.DatabaseMutationBuilder
import com.prisma.api.database.mutactions.{ClientSqlDataChangeMutaction, ClientSqlStatementResult}

import scala.concurrent.Future

case class TruncateTable(projectId: String, tableName: String) extends ClientSqlDataChangeMutaction {

  override def execute: Future[ClientSqlStatementResult[Any]] =
    Future.successful(ClientSqlStatementResult(sqlAction = DatabaseMutationBuilder.truncateTable(projectId, tableName)))
}
