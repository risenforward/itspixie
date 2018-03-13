package com.prisma.api.database.mutactions.mutactions

import java.sql.SQLIntegrityConstraintViolationException

import com.prisma.api.connector.{NodeEdge, Path}
import com.prisma.api.database.mutactions._
import com.prisma.api.database.mutactions.validation.InputValueValidation
import com.prisma.api.database.{DatabaseMutationBuilder, ProjectRelayId, ProjectRelayIdTable}
import com.prisma.api.mutations.CoolArgs
import com.prisma.api.schema.APIErrors
import com.prisma.shared.models._
import com.prisma.util.json.JsonFormats
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.Future
import scala.util.Try

case class CreateDataItem(
    project: Project,
    path: Path,
    args: CoolArgs
) extends ClientSqlDataChangeMutaction {

  val model = path.lastModel
  val where = path.edges match {
    case x if x.isEmpty => path.root
    case x              => x.last.asInstanceOf[NodeEdge].childWhere
  }

  val id = where.fieldValueAsString

  override def execute: Future[ClientSqlStatementResult[Any]] = {
    val relayIds = TableQuery(new ProjectRelayIdTable(_, project.id))

    Future.successful(
      ClientSqlStatementResult(
        sqlAction = DBIO.seq(
          DatabaseMutationBuilder.createDataItem(project.id, model.name, args.generateNonListCreateArgs(model, id)),
          relayIds += ProjectRelayId(id = id, model.stableIdentifier)
        )))
  }

  override def handleErrors = {
    implicit val anyFormat = JsonFormats.AnyJsonFormat
    Some({
      case e: SQLIntegrityConstraintViolationException if e.getErrorCode == 1062 && GetFieldFromSQLUniqueException.getFieldOption(List(args), e).isDefined =>
        APIErrors.UniqueConstraintViolation(model.name, GetFieldFromSQLUniqueException.getFieldOption(List(args), e).get)
      case e: SQLIntegrityConstraintViolationException if e.getErrorCode == 1452 =>
        APIErrors.NodeDoesNotExist("")
    })
  }

  override def verify(): Try[MutactionVerificationSuccess] = {
    val (check, _) = InputValueValidation.validateDataItemInputs(model, args)
    check
  }
}
