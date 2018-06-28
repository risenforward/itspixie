package com.prisma.api.connector.jdbc.database

import java.sql.ResultSet

import com.prisma.api.connector.jdbc.extensions.JdbcExtensions
import com.prisma.api.connector.{PrismaNode, PrismaNodeWithParent, RelationNode, ScalarListElement}
import com.prisma.gc_values.RootGCValue
import com.prisma.shared.models._
import com.prisma.slick.NewJdbcExtensions.ReadsResultSet

trait ResultSetReaders extends JdbcExtensions with QueryBuilderConstants {
  val readsAsUnit: ReadsResultSet[Unit] = ReadsResultSet(_ => ())

  def readNodeId(model: Model) = ReadsResultSet(_.getId(model))

  def readPrismaNodeWithParent(rf: RelationField): ReadsResultSet[PrismaNodeWithParent] = ReadsResultSet { rs =>
    val node = readPrismaNode(rf.relatedModel_!, rs)

    val parentId = if (rf.relation.isSameModelRelation) {
      val firstSide  = rs.getParentId(RelationSide.A, rf.model.idField_!.typeIdentifier)
      val secondSide = rs.getParentId(RelationSide.B, rf.model.idField_!.typeIdentifier)
      if (firstSide == node.id) secondSide else firstSide
    } else {
      val parentRelationSide = rf.relation.modelA match {
        case x if x == rf.relatedModel_! => RelationSide.B
        case _                           => RelationSide.A
      }
      rs.getParentId(parentRelationSide, rf.model.idField_!.typeIdentifier)
    }
    PrismaNodeWithParent(parentId, node)
  }

  def readsPrismaNode(model: Model): ReadsResultSet[PrismaNode] = ReadsResultSet { rs =>
    readPrismaNode(model, rs)
  }

  private def readPrismaNode(model: Model, rs: ResultSet) = {
    val data = model.scalarNonListFields.map(field => field.name -> rs.getGcValue(field.dbName, field.typeIdentifier))
    PrismaNode(id = rs.getId(model), data = RootGCValue(data: _*), Some(model.name))
  }

  def readsScalarListField(field: ScalarField): ReadsResultSet[ScalarListElement] = ReadsResultSet { rs =>
    val nodeId   = rs.getString(nodeIdFieldName)
    val position = rs.getInt(positionFieldName)
    val value    = rs.getGcValue(valueFieldName, field.typeIdentifier)
    ScalarListElement(nodeId, position, value)
  }

  def readRelation(relation: Relation): ReadsResultSet[RelationNode] = ReadsResultSet { resultSet =>
    RelationNode(
      a = resultSet.getAsID("A", relation.modelA.idField_!.typeIdentifier),
      b = resultSet.getAsID("B", relation.modelB.idField_!.typeIdentifier)
    )
  }
}
