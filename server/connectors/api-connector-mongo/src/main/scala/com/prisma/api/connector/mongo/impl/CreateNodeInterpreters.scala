package com.prisma.api.connector.mongo.impl

import com.prisma.api.connector._
import com.prisma.api.connector.mongo.database.{MongoAction, MongoActionsBuilder, SequenceAction, SimpleMongoAction}
import com.prisma.api.connector.mongo.{NestedDatabaseMutactionInterpreter, TopLevelDatabaseMutactionInterpreter}
import com.prisma.api.schema.APIErrors
import com.prisma.gc_values.ListGCValue
import com.prisma.shared.models.Manifestations.EmbeddedRelationLink
import com.prisma.shared.models.Model
import org.mongodb.scala.MongoWriteException

import scala.concurrent.ExecutionContext

case class CreateNodeInterpreter(mutaction: CreateNode)(implicit ec: ExecutionContext) extends TopLevelDatabaseMutactionInterpreter {
  override def mongoAction(mutationBuilder: MongoActionsBuilder): SimpleMongoAction[MutactionResults] = {
    mutationBuilder.createNode(mutaction, List.empty)
  }

  override val errorMapper = {
    case e: MongoWriteException if e.getError.getCode == 11000 && MongoErrorMessageHelper.getFieldOption(mutaction.model, e).isDefined =>
      APIErrors.UniqueConstraintViolation(mutaction.model.name, MongoErrorMessageHelper.getFieldOption(mutaction.model, e).get)
  }
}

object MongoErrorMessageHelper {

  def getFieldOption(model: Model, e: MongoWriteException): Option[String] = {
    model.scalarFields.filter { field =>
      val constraintName = field.name + "_U"
      e.getMessage.contains(constraintName)
    } match {
      case x +: _ => Some("Field name = " + x.name)
      case _      => None
    }
  }
}

case class NestedCreateNodeInterpreter(mutaction: NestedCreateNode)(implicit val ec: ExecutionContext)
    extends NestedDatabaseMutactionInterpreter
    with NestedRelationInterpreterBase {
  override def relationField = mutaction.relationField
  val model                  = p.relatedModel_!

  override def mongoAction(mutationBuilder: MongoActionsBuilder, parent: NodeAddress) = {
    implicit val mb: MongoActionsBuilder = mutationBuilder

    for {
      _       <- SequenceAction(Vector(requiredCheck(parent), removalAction(parent)))
      results <- createNodeAndConnectToParent(mutationBuilder, parent)
    } yield results
  }

  private def createNodeAndConnectToParent(
      mutationBuilder: MongoActionsBuilder,
      parent: NodeAddress
  )(implicit ec: ExecutionContext): MongoAction[MutactionResults] = relation.manifestation match {
    case Some(m: EmbeddedRelationLink) if m.inTableOfModelName == model.name => // ID is stored on this Node
      val inlineRelation = c.isList match {
        case true  => List((m.referencingColumn, ListGCValue(Vector(parent.idValue))))
        case false => List((m.referencingColumn, parent.idValue))
      }

      for {
        mutactionResult <- mutationBuilder.createNode(mutaction, inlineRelation)
        id              = mutactionResult.results.find(_.mutaction == mutaction).get.asInstanceOf[CreateNodeResult].id
      } yield mutactionResult

    case _ => // ID is stored on other node, we need to update the parent with the inline relation id after creating the child.
      for {
        mutactionResult <- mutationBuilder.createNode(mutaction, List.empty)
        id              = mutactionResult.results.find(_.mutaction == mutaction).get.asInstanceOf[CreateNodeResult].id
        _               <- mutationBuilder.createRelation(mutaction.relationField, parent, id)
      } yield mutactionResult
  }

  def requiredCheck(parent: NodeAddress)(implicit mutationBuilder: MongoActionsBuilder) = mutaction.topIsCreate match {
    case false =>
      (p.isList, p.isRequired, c.isList, c.isRequired) match {
        case (false, true, false, true)   => requiredRelationViolation
        case (false, true, false, false)  => noCheckRequired
        case (false, false, false, true)  => checkForOldChild(parent)
        case (false, false, false, false) => noCheckRequired
        case (true, false, false, true)   => noCheckRequired
        case (true, false, false, false)  => noCheckRequired
        case (false, true, true, false)   => noCheckRequired
        case (false, false, true, false)  => noCheckRequired
        case (true, false, true, false)   => noCheckRequired
        case _                            => errorBecauseManySideIsRequired
      }

    case true =>
      noCheckRequired
  }

  def removalAction(parent: NodeAddress)(implicit mutationBuilder: MongoActionsBuilder) = mutaction.topIsCreate match {
    case false =>
      (p.isList, c.isList) match {
        case (false, false) => removalByParent(parent)
        case (true, false)  => noActionRequired
        case (false, true)  => removalByParent(parent)
        case (true, true)   => noActionRequired
      }

    case true =>
      noActionRequired
  }

  override val errorMapper = {
    case e: MongoWriteException if e.getError.getCode == 11000 && MongoErrorMessageHelper.getFieldOption(mutaction.model, e).isDefined =>
      APIErrors.UniqueConstraintViolation(mutaction.model.name, MongoErrorMessageHelper.getFieldOption(mutaction.model, e).get)
  }
}
