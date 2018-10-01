package com.prisma.api.connector.mongo.impl

import com.prisma.api.connector._
import com.prisma.api.connector.mongo.TopLevelDatabaseMutactionInterpreter
import com.prisma.api.connector.mongo.database.{MongoAction, MongoActionsBuilder, SequenceAction, SimpleMongoAction}
import com.prisma.gc_values.IdGCValue

import scala.concurrent.ExecutionContext

case class DeleteNodesInterpreter(mutaction: DeleteNodes)(implicit ec: ExecutionContext) extends TopLevelDatabaseMutactionInterpreter {

  def mongoAction(mutationBuilder: MongoActionsBuilder): SimpleMongoAction[MutactionResults] = mutationBuilder.deleteNodes(mutaction)

  private def checkForRequiredRelationsViolations(mutationBuilder: MongoActionsBuilder, nodeIds: Vector[IdGCValue]): MongoAction[MutactionResults] = {
//    val fieldsWhereThisModelIsRequired = mutaction.project.schema.fieldsWhereThisModelIsRequired(mutaction.model)
//    val actions                        = fieldsWhereThisModelIsRequired.map(field => mutationBuilder.errorIfNodesAreInRelation(nodeIds, field)).toVector
//
//    SequenceAction(actions)
    ???
  }
}

case class ResetDataInterpreter(mutaction: ResetData)(implicit ec: ExecutionContext) extends TopLevelDatabaseMutactionInterpreter {
  def mongoAction(mutationBuilder: MongoActionsBuilder): SimpleMongoAction[MutactionResults] = {
    mutationBuilder.truncateTables(mutaction)
  }
}

case class UpdateNodesInterpreter(mutaction: UpdateNodes)(implicit ec: ExecutionContext) extends TopLevelDatabaseMutactionInterpreter {
  def mongoAction(mutationBuilder: MongoActionsBuilder): SimpleMongoAction[MutactionResults] = {
    mutationBuilder.updateNodes(mutaction)
  }
}
