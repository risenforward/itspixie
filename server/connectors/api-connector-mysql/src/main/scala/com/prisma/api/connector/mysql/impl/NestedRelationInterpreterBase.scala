package com.prisma.api.connector.mysql.impl

import java.sql.SQLException

import com.prisma.api.connector.mysql.DatabaseMutactionInterpreter
import com.prisma.api.connector.mysql.database.ErrorMessageParameterHelper.parameterString
import com.prisma.api.connector.mysql.database.MySqlApiDatabaseMutationBuilder._
import com.prisma.api.connector.{Edge, ModelEdge, NodeEdge, Path}
import com.prisma.api.schema.APIErrors.RequiredRelationWouldBeViolated
import com.prisma.shared.models.{Model, Project, RelationField}
import slick.dbio.{DBIOAction, Effect, NoStream}

trait NestedRelationInterpreterBase extends DatabaseMutactionInterpreter {

  def path: Path
  def project: Project
  def topIsCreate: Boolean

  val lastEdge: Edge    = path.lastEdge_!
  val p: RelationField  = lastEdge.parentField
  val otherModel: Model = lastEdge.child
  val c: RelationField  = lastEdge.childField

  def checkForOldParent = oldParentFailureTrigger(project, path)
  def checkForOldParentByChildWhere = path.lastEdge_! match {
    case _: ModelEdge   => sys.error("Should be a node edge")
    case edge: NodeEdge => oldParentFailureTriggerForRequiredRelations(project, edge.relation, edge.childWhere, edge.childRelationSide)
  }

  def checkForOldChild                = oldChildFailureTrigger(project, path)
  def noCheckRequired: List[Nothing]  = List.empty
  def removalByParent                 = deleteRelationRowByParent(project.id, path)
  def removalByChildWhere             = deleteRelationRowByChildWithWhere(project.id, path)
  def removalByParentAndChild         = deleteRelationRowByParentAndChild(project.id, path)
  def createRelationRow               = List(createRelationRowByPath(project.id, path))
  def noActionRequired: List[Nothing] = List.empty
  def requiredCheck: List[DBIOAction[_, NoStream, Effect]]
  def removalActions: List[DBIOAction[_, NoStream, Effect]]
  def addAction: List[DBIOAction[_, NoStream, Effect]]
  def allActions: List[DBIOAction[_, NoStream, Effect]] = requiredCheck ++ removalActions ++ addAction

  override val action = DBIOAction.seq(allActions: _*)

  override val errorMapper: PartialFunction[Throwable, Nothing] = {
    case e: SQLException if e.getErrorCode == 1242 && causedByThisMutaction(path, e.getCause.toString) =>
      throw RequiredRelationWouldBeViolated(project, path.lastRelation_!)
  }

  def requiredRelationViolation = throw RequiredRelationWouldBeViolated(project, path.lastRelation_!)

  def sysError = sys.error("This should not happen, since it means a many side is required")

  def causedByThisMutaction(path: Path, cause: String): Boolean = {
    val parentCheckString = s"`${path.lastRelation_!.relationTableName}` OLDPARENTFAILURETRIGGER WHERE `${path.lastEdge_!.childRelationSide}`"
    val childCheckString  = s"`${path.lastRelation_!.relationTableName}` OLDCHILDPATHFAILURETRIGGER WHERE `${path.lastEdge_!.parentRelationSide}`"

    val parentParameterString = path.lastEdge_! match {
      case edge: NodeEdge => parameterString(edge.childWhere)
      case _: ModelEdge   => ""
    }

    val childParameterString = path.edges.length match {
      case 0 =>
        sys.error("There should always be at least one edge on the path if this is called.")

      case 1 =>
        parameterString(path.root)

      case _ =>
        path.removeLastEdge.lastEdge_! match {
          case edge: NodeEdge => parameterString(edge.childWhere)
          case _: ModelEdge   => ""
        }
    }

    (cause.contains(parentCheckString) && cause.contains(parentParameterString)) ||
    (cause.contains(childCheckString) && cause.contains(childParameterString))
  }
}
