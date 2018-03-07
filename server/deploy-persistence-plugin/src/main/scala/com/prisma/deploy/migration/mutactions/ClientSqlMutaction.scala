package com.prisma.deploy.migration.mutactions

import com.prisma.shared.models.{Field, Model}
import slick.dbio.{DBIOAction, Effect, NoStream}

import scala.concurrent.Future
import scala.util.{Success, Try}

trait ClientSqlMutaction {
//  def verify(): Future[Try[Unit]] = Future.successful(Success(()))
//
//  def execute: Future[ClientSqlStatementResult[Any]]
//
//  def rollback: Option[Future[ClientSqlStatementResult[Any]]] = None
}

case class ClientSqlStatementResult[A <: Any](sqlAction: DBIOAction[A, NoStream, Effect.All])

case class CreateClientDatabaseForProject(projectId: String) extends ClientSqlMutaction
case class DeleteClientDatabaseForProject(projectId: String) extends ClientSqlMutaction
// those should be named fields
case class CreateColumn(projectId: String, model: Model, field: Field)                     extends ClientSqlMutaction
case class DeleteColumn(projectId: String, model: Model, field: Field)                     extends ClientSqlMutaction
case class UpdateColumn(projectId: String, model: Model, oldField: Field, newField: Field) extends ClientSqlMutaction

trait AnyMutactionExecutor {
  def execute(mutaction: ClientSqlMutaction): Future[Unit]
  def rollback(mutaction: ClientSqlMutaction): Future[Unit]
}

trait MutactionExecutor[T <: ClientSqlMutaction] {
  import slick.jdbc.MySQLProfile.backend.DatabaseDef
  def execute(mutaction: T, database: DatabaseDef): Future[Unit]
  def rollback(mutaction: T, database: DatabaseDef): Future[Unit]
}

object FailingAnyMutactionExecutor extends AnyMutactionExecutor {
  override def execute(mutaction: ClientSqlMutaction) = Future.failed(new Exception(this.getClass.getSimpleName))

  override def rollback(mutaction: ClientSqlMutaction) = Future.failed(new Exception(this.getClass.getSimpleName))
}
