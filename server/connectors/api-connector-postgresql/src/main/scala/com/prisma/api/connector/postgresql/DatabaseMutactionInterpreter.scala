package com.prisma.api.connector.postgresql

import com.prisma.api.connector.{DatabaseMutactionResult, UnitDatabaseMutactionResult}
import com.prisma.api.schema.UserFacingError
import slick.dbio.{DBIO, DBIOAction, Effect, NoStream}

trait DatabaseMutactionInterpreter {
  private val unitResult = DBIO.successful(UnitDatabaseMutactionResult)

  def newAction: DBIO[DatabaseMutactionResult] = action.andThen(unitResult)

  def action: DBIOAction[Any, NoStream, Effect.All]

  def errorMapper: PartialFunction[Throwable, UserFacingError] = PartialFunction.empty
}
