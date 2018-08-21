package com.prisma.api.connector.mongo.database

import com.prisma.api.connector.mongo.DocumentToRoot
import com.prisma.api.connector.mongo.database.NodeSelectorBsonTransformer.WhereToBson
import com.prisma.api.connector.{Filter, NodeSelector, PrismaNode, SelectedFields}
import com.prisma.gc_values.IdGCValue
import com.prisma.shared.models.{Model, RelationField, Schema}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.language.existentials

trait NodeSingleQueries extends FilterConditionBuilder {

  def getModelForGlobalId(schema: Schema, idGCValue: IdGCValue) = ???

  def getNodeByWhere(where: NodeSelector, database: MongoDatabase): Future[Option[PrismaNode]] =
    getNodeByWhere(where, SelectedFields.all(where.model), database)

  def getNodeByWhere(where: NodeSelector, selectedFields: SelectedFields, database: MongoDatabase): Future[Option[PrismaNode]] = {
    val collection: MongoCollection[Document] = database.getCollection(where.model.dbName)
    collection.find(WhereToBson(where)).collect().toFuture.map { results: Seq[Document] =>
      results.headOption.map { result =>
        val root = DocumentToRoot(where.model, result)
        PrismaNode(root.idField, root)
      }
    }
  }

  def getNodeIdByWhere(where: NodeSelector, database: MongoDatabase) = {
    val collection: MongoCollection[Document] = database.getCollection(where.model.dbName)
    collection.find(WhereToBson(where)).collect().toFuture.map { results: Seq[Document] =>
      results.headOption.map { result =>
        val root = DocumentToRoot(where.model, result)
        root.idField
      }
    }
  }

  def getNodeIdByParentId(parentField: RelationField, parentId: IdGCValue)(implicit ec: ExecutionContext) = ???

  def getNodeIdsByParentIds(parentField: RelationField, parentIds: Vector[IdGCValue]) = ???

  def getNodeIdsByFilter(model: Model, filter: Option[Filter], database: MongoDatabase) = {
    val collection: MongoCollection[Document] = database.getCollection(model.dbName)
    val bsonFilter: Bson                      = buildConditionForFilter(filter)
    collection.find(bsonFilter).collect().toFuture.map { results: Seq[Document] =>
      results.map { result =>
        val root = DocumentToRoot(model, result)
        val res  = root.map("id").asInstanceOf[IdGCValue]
        res
      }
    }
  }

  def getNodeIdByParentIdAndWhere(parentField: RelationField, parentId: IdGCValue, where: NodeSelector) = ???

  private def parentIdCondition(parentField: RelationField) = ???

  private def parentIdCondition(parentField: RelationField, parentIds: Vector[Any]) = ???
}
