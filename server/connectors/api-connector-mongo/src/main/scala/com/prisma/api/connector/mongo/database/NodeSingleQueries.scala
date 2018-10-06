package com.prisma.api.connector.mongo.database

import com.prisma.api.connector._
import com.prisma.api.connector.mongo.extensions.NodeSelectorBsonTransformer.whereToBson
import com.prisma.api.connector.mongo.extensions.{DocumentToId, DocumentToRoot}
import com.prisma.gc_values.{CuidGCValue, IdGCValue, ListGCValue}
import com.prisma.shared.models.{Model, Project, RelationField}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.{Document, MongoCollection}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.existentials

trait NodeSingleQueries extends FilterConditionBuilder {

  def getModelForGlobalId(project: Project, globalId: CuidGCValue) = SimpleMongoAction { database =>
    val outer = project.models.map { model =>
      val collection: MongoCollection[Document] = database.getCollection(model.dbName)
      collection.find(Filters.eq("_id", globalId.value)).collect().toFuture.map { results: Seq[Document] =>
        if (results.nonEmpty) Vector(model) else Vector.empty
      }
    }

    val sequence: Future[List[Vector[Model]]] = Future.sequence(outer)

    sequence.map(_.flatten.headOption)
  }

  def getNodeByWhere(where: NodeSelector, selectedFields: SelectedFields) = SimpleMongoAction { database =>
    val collection: MongoCollection[Document] = database.getCollection(where.model.dbName)
    collection.find(where).collect().toFuture.map { results: Seq[Document] =>
      results.headOption.map { result =>
        val root = DocumentToRoot(where.model, result)
        PrismaNode(root.idField, root, Some(where.model.name))
      }
    }
  }

  def getNodeIdByWhere(where: NodeSelector) = SimpleMongoAction { database =>
    val collection: MongoCollection[Document] = database.getCollection(where.model.dbName)
    collection.find(where).projection(include("_.id")).collect().toFuture.map(res => res.headOption.map(DocumentToId.toCUIDGCValue))
  }

  def getNodeIdByParentId(parentField: RelationField, parentId: IdGCValue): MongoAction[Option[IdGCValue]] = {
    val parentModel = parentField.model
    val childModel  = parentField.relatedModel_!

    parentField.relationIsInlinedInParent match {
      case true =>
        getNodeByWhere(NodeSelector.forId(parentModel, parentId), SelectedFields.all(parentModel)).map {
          case None => None
          case Some(n) =>
            n.data.map(parentField.name) match {
              case x: CuidGCValue => Some(x)
              case _              => None
            }
        }

      case false =>
        val filter = parentField.relatedField.isList match {
          case false => Some(ScalarFilter(childModel.idField_!.copy(name = parentField.relatedField.dbName), Equals(parentId)))
          case true  => Some(ScalarFilter(childModel.idField_!.copy(name = parentField.relatedField.dbName, isList = true), Contains(parentId)))
        }

        getNodeIdsByFilter(childModel, filter).map(_.headOption)
    }
  }

  def getNodeIdsByFilter(model: Model, filter: Option[Filter]) = SimpleMongoAction { database =>
    val collection: MongoCollection[Document] = database.getCollection(model.dbName)
    val bsonFilter: Bson                      = buildConditionForFilter(filter)
    collection.find(bsonFilter).projection(include("_.id")).collect().toFuture.map(res => res.map(DocumentToId.toCUIDGCValue))
  }

  def getNodeIdByParentIdAndWhere(parentField: RelationField, parentId: IdGCValue, where: NodeSelector): MongoAction[Option[IdGCValue]] = {
    val parentModel = parentField.model
    val childModel  = parentField.relatedModel_!

    parentField.relationIsInlinedInParent match { //parent contains one or more ids, one of them matches the child returned for the where
      case true =>
        getNodeByWhere(NodeSelector.forId(parentModel, parentId), SelectedFields.all(parentModel)).flatMap {
          case None =>
            noneHelper

          case Some(n) =>
            (parentField.isList, n.data.map(parentField.name)) match {
              case (false, idInParent: CuidGCValue) =>
                getNodeByWhere(where, SelectedFields.all(where.model)).map {
                  case Some(childForWhere) if idInParent == childForWhere.id => Some(idInParent)
                  case _                                                     => None
                }

              case (true, ListGCValue(values)) =>
                getNodeByWhere(where, SelectedFields.all(where.model)).map {
                  case Some(childForWhere) if values.contains(childForWhere.id) => Some(childForWhere.id)
                  case _                                                        => None
                }

              case _ =>
                noneHelper
            }
        }
      case false => //child id that matches the where contains the parent
        val parentFilter = parentField.relatedField.isList match {
          case false => ScalarFilter(childModel.idField_!.copy(name = parentField.relatedField.dbName), Equals(parentId))
          case true  => ScalarFilter(childModel.idField_!.copy(name = parentField.relatedField.dbName, isList = true), Contains(parentId))
        }
        val whereFilter = ScalarFilter(where.field, Equals(where.fieldGCValue))
        val filter      = Some(AndFilter(Vector(parentFilter, whereFilter)))

        getNodeIdsByFilter(childModel, filter).map(_.headOption)
    }
  }

  def noneHelper = SimpleMongoAction { database =>
    Future(Option.empty[IdGCValue])
  }

}
