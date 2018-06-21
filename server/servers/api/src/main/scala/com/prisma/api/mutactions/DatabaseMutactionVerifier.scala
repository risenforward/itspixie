package com.prisma.api.mutactions

import com.prisma.api.connector._
import com.prisma.api.mutactions.validation.InputValueValidation
import com.prisma.api.schema.APIErrors.ClientApiError
import com.prisma.api.schema.UserFacingError
import com.prisma.shared.models.Model

trait DatabaseMutactionVerifier {
  def verify(mutactions: Vector[DatabaseMutaction]): Vector[UserFacingError]
}

object DatabaseMutactionVerifierImpl extends DatabaseMutactionVerifier {
  override def verify(mutactions: Vector[DatabaseMutaction]): Vector[UserFacingError] = {
    mutactions.flatMap {
      case m: CreateDataItem       => verify(m)
      case m: UpdateDataItem       => verify(m)
      case m: UpsertDataItem       => verify(m)
      case m: NestedUpsertDataItem => verify(m)
      case _                       => None
    }
  }

  def verify(mutaction: CreateDataItem): Option[ClientApiError] = InputValueValidation.validateDataItemInputs(mutaction.model, mutaction.nonListArgs)
  def verify(mutaction: UpdateDataItem): Option[ClientApiError] = InputValueValidation.validateDataItemInputs(mutaction.where.model, mutaction.nonListArgs)

  def verify(mutaction: UpsertDataItem): Iterable[ClientApiError] = {
    val model      = mutaction.where.model
    val createArgs = mutaction.nonListCreateArgs
    val updateArgs = mutaction.nonListUpdateArgs
    verifyUpsert(model, createArgs, updateArgs)
  }

  def verify(mutaction: NestedUpsertDataItem): Iterable[ClientApiError] = {
    verifyUpsert(mutaction.createPath.lastModel, mutaction.createNonListArgs, mutaction.updateNonListArgs)
  }

  def verifyUpsert(model: Model, createArgs: PrismaArgs, updateArgs: PrismaArgs): Iterable[ClientApiError] = {
    val createCheck = InputValueValidation.validateDataItemInputs(model, createArgs)
    val updateCheck = InputValueValidation.validateDataItemInputs(model, updateArgs)
    createCheck ++ updateCheck
  }
}
