package com.prisma.api.connector.postgresql.impl

import com.prisma.api.connector.NestedConnectRelation
import com.prisma.api.connector.postgresql.database.PostgresApiDatabaseMutationBuilder
import slick.dbio.{DBIO, Effect, NoStream}
import slick.sql.{SqlAction, SqlStreamingAction}

case class NestedConnectRelationInterpreter(mutaction: NestedConnectRelation) extends NestedRelationInterpreterBase {
  override def path        = mutaction.path
  override def project     = mutaction.project
  override def topIsCreate = mutaction.topIsCreate

  override def requiredCheck(implicit mutationBuilder: PostgresApiDatabaseMutationBuilder): List[SqlStreamingAction[Vector[String], String, Effect]] =
    topIsCreate match {
      case false =>
        (p.isList, p.isRequired, c.isList, c.isRequired) match {
          case (false, true, false, true)   => requiredRelationViolation
          case (false, true, false, false)  => List(checkForOldParentByChildWhere)
          case (false, false, false, true)  => List(checkForOldChild)
          case (false, false, false, false) => noCheckRequired
          case (true, false, false, true)   => noCheckRequired
          case (true, false, false, false)  => noCheckRequired
          case (false, true, true, false)   => noCheckRequired
          case (false, false, true, false)  => noCheckRequired
          case (true, false, true, false)   => noCheckRequired
          case _                            => sysError
        }
      case true =>
        (p.isList, p.isRequired, c.isList, c.isRequired) match {
          case (false, true, false, true)   => requiredRelationViolation
          case (false, true, false, false)  => List(checkForOldParentByChildWhere)
          case (false, false, false, true)  => noActionRequired
          case (false, false, false, false) => noActionRequired
          case (true, false, false, true)   => noActionRequired
          case (true, false, false, false)  => noActionRequired
          case (false, true, true, false)   => noActionRequired
          case (false, false, true, false)  => noActionRequired
          case (true, false, true, false)   => noActionRequired
          case _                            => sysError
        }
    }

  override def removalActions(implicit mutationBuilder: PostgresApiDatabaseMutationBuilder): List[DBIO[Unit]] =
    topIsCreate match {
      case false =>
        (p.isList, p.isRequired, c.isList, c.isRequired) match {
          case (false, true, false, true)   => requiredRelationViolation
          case (false, true, false, false)  => List(removalByParent)
          case (false, false, false, true)  => List(removalByParent, removalByChildWhere)
          case (false, false, false, false) => List(removalByParent, removalByChildWhere)
          case (true, false, false, true)   => List(removalByChildWhere)
          case (true, false, false, false)  => List(removalByChildWhere)
          case (false, true, true, false)   => List(removalByParent)
          case (false, false, true, false)  => List(removalByParent)
          case (true, false, true, false)   => noActionRequired
          case _                            => sysError
        }
      case true =>
        (p.isList, p.isRequired, c.isList, c.isRequired) match {
          case (false, true, false, true)   => requiredRelationViolation
          case (false, true, false, false)  => noActionRequired
          case (false, false, false, true)  => List(removalByChildWhere)
          case (false, false, false, false) => List(removalByChildWhere)
          case (true, false, false, true)   => List(removalByChildWhere)
          case (true, false, false, false)  => List(removalByChildWhere)
          case (false, true, true, false)   => noActionRequired
          case (false, false, true, false)  => noActionRequired
          case (true, false, true, false)   => noActionRequired
          case _                            => sysError
        }
    }

  override def addAction(implicit mutationBuilder: PostgresApiDatabaseMutationBuilder): List[SqlAction[Int, NoStream, Effect]] = createRelationRow
}
