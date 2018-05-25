package com.prisma.shared.models

import com.prisma.shared.models.Manifestations.{InlineRelationManifestation, RelationManifestation, RelationTableManifestation}

import scala.language.implicitConversions

case class RelationTemplate(
    name: String,
    // BEWARE: if the relation looks like this: val relation = Relation(id = "relationId", modelA = "userId", modelB = "todoId")
    // then the relationSide for the fields have to be "opposite", because the field's side is the side of _the other_ model
    // val userField = RelationField(..., relation = relation, relationSide = RelationSide.B)
    // val todoField = RelationField(..., relation = relation, relationSide = RelationSide.A)
    modelAName: String,
    modelBName: String,
    modelAOnDelete: OnDelete.Value,
    modelBOnDelete: OnDelete.Value,
    manifestation: Option[RelationManifestation]
) {
  def build(schema: Schema) = new Relation(this, schema)

  def connectsTheModels(model1: String, model2: String): Boolean =
    (modelAName == model1 && modelBName == model2) || (modelAName == model2 && modelBName == model1)

  def isSameModelRelation: Boolean = modelAName == modelBName
}

object Relation {
  implicit def asRelationTemplate(relation: Relation): RelationTemplate = relation.template
}

class Relation(
    val template: RelationTemplate,
    val schema: Schema
) {
  import template._

  lazy val bothSidesCascade: Boolean                                = modelAOnDelete == OnDelete.Cascade && modelBOnDelete == OnDelete.Cascade
  lazy val modelA: Model                                            = schema.getModelByName_!(modelAName)
  lazy val modelB: Model                                            = schema.getModelByName_!(modelBName)
  lazy val modelAField: Option[RelationField]                       = modelFieldFor(modelAName, RelationSide.A)
  lazy val modelBField: Option[RelationField]                       = modelFieldFor(modelBName, RelationSide.B)
  lazy val hasManifestation: Boolean                                = manifestation.isDefined
  lazy val isInlineRelation: Boolean                                = manifestation.exists(_.isInstanceOf[InlineRelationManifestation])
  lazy val inlineManifestation: Option[InlineRelationManifestation] = manifestation.collect { case x: InlineRelationManifestation => x }
  // note: defaults to modelAField to handle same model, same field relations
  lazy val isSameFieldSameModelRelation: Boolean = modelAField == modelBField.orElse(modelAField)

  lazy val relationTableName = manifestation match {
    case Some(m: RelationTableManifestation)  => m.table
    case Some(m: InlineRelationManifestation) => schema.getModelByName_!(m.inTableOfModelId).dbName
    case None                                 => "_" + name
  }

  lazy val modelAColumn: String = manifestation match {
    case Some(m: RelationTableManifestation)  => m.modelAColumn
    case Some(m: InlineRelationManifestation) => if (m.inTableOfModelId == modelAName) modelA.idField_!.dbName else m.referencingColumn
    case None                                 => "A"
  }

  lazy val modelBColumn: String = manifestation match {
    case Some(m: RelationTableManifestation)  => m.modelBColumn
    case Some(m: InlineRelationManifestation) => if (m.inTableOfModelId == modelBName && !isSameModelRelation) modelB.idField_!.dbName else m.referencingColumn
    case None                                 => "B"
  }

  lazy val isManyToMany: Boolean = {
    val modelAFieldIsList = modelAField.map(_.isList).getOrElse(true)
    val modelBFieldIsList = modelBField.map(_.isList).getOrElse(true)
    modelAFieldIsList && modelBFieldIsList
  }

  private def modelFieldFor(model: String, relationSide: RelationSide.Value): Option[RelationField] = {
    val model = relationSide match {
      case RelationSide.A => modelA
      case RelationSide.B => modelB
    }
    model.relationFields.find(_.isRelationWithNameAndSide(name, relationSide))
  }

  def columnForRelationSide(relationSide: RelationSide.Value): String = if (relationSide == RelationSide.A) modelAColumn else modelBColumn

  def getFieldOnModel(modelId: String): Option[RelationField] = {
    modelId match {
      case `modelAName` => modelAField
      case `modelBName` => modelBField
      case _            => sys.error(s"The model id ${modelId} is not part of this relation ${name}")
    }
  }

  def sideOfModelCascades(model: Model): Boolean = {
    model.name match {
      case `modelAName` => modelAOnDelete == OnDelete.Cascade
      case `modelBName` => modelBOnDelete == OnDelete.Cascade
      case _            => sys.error(s"The model ${model.name} is not part of the relation $name")
    }
  }
}
