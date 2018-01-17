package com.prisma.api.database.mutactions.mutactions

import java.sql.SQLIntegrityConstraintViolationException

import com.prisma.api.database.mutactions.GetFieldFromSQLUniqueException._
import com.prisma.api.database.mutactions.validation.InputValueValidation
import com.prisma.api.database.mutactions.{ClientSqlDataChangeMutaction, ClientSqlStatementResult, MutactionVerificationSuccess}
import com.prisma.api.database.{DataResolver, DatabaseMutationBuilder}
import com.prisma.api.mutations.{CoolArgs, NodeSelector}
import com.prisma.api.schema.APIErrors
import cool.graph.cuid.Cuid
import com.prisma.shared.models.{Model, Project}
import com.prisma.util.gc_value.GCStringConverter
import com.prisma.util.json.JsonFormats

import scala.concurrent.Future
import scala.util.{Success, Try}

case class UpsertDataItem(
    project: Project,
    model: Model,
    createArgs: CoolArgs,
    updateArgs: CoolArgs,
    where: NodeSelector
) extends ClientSqlDataChangeMutaction {

  override def execute: Future[ClientSqlStatementResult[Any]] = Future.successful {
    ClientSqlStatementResult(DatabaseMutationBuilder.upsert(project, where, createArgs, updateArgs))
  }

  override def handleErrors = {
    implicit val anyFormat = JsonFormats.AnyJsonFormat
    Some({
      case e: SQLIntegrityConstraintViolationException if e.getErrorCode == 1062 && getFieldOptionFromCoolArgs(List(createArgs, updateArgs), e).isDefined =>
        APIErrors.UniqueConstraintViolation(model.name, getFieldOptionFromCoolArgs(List(createArgs, updateArgs), e).get)
      case e: SQLIntegrityConstraintViolationException if e.getErrorCode == 1452 => APIErrors.NodeDoesNotExist(where.fieldValueAsString)
      case e: SQLIntegrityConstraintViolationException if e.getErrorCode == 1048 => APIErrors.FieldCannotBeNull()
    })
  }
  override def verify(resolver: DataResolver): Future[Try[MutactionVerificationSuccess]] = {
    val (createCheck, _) = InputValueValidation.validateDataItemInputs(model, createArgs.nonListScalarArguments(model).toList)
    val (updateCheck, _) = InputValueValidation.validateDataItemInputs(model, updateArgs.nonListScalarArguments(model).toList)

    (createCheck.isFailure, updateCheck.isFailure) match {
      case (true, _)      => Future.successful(createCheck)
      case (_, true)      => Future.successful(updateCheck)
      case (false, false) => Future.successful(Success(MutactionVerificationSuccess()))
    }
  }
}
