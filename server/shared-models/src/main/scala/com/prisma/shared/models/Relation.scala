package com.prisma.shared.models

import com.prisma.shared.models.Manifestations.{EmbeddedRelationLink, RelationLinkManifestation, RelationTable}

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
    manifestation: Option[RelationLinkManifestation]
) {
  def build(schema: Schema) = new Relation(this, schema)

  def connectsTheModels(model1: String, model2: String): Boolean =
    (modelAName == model1 && modelBName == model2) || (modelAName == model2 && modelBName == model1)

  def isSelfRelation: Boolean = modelAName == modelBName
}

object Relation {
  implicit def asRelationTemplate(relation: Relation): RelationTemplate = relation.template
}

class Relation(
    val template: RelationTemplate,
    val schema: Schema
) {
  import template._

  lazy val bothSidesCascade: Boolean                         = modelAOnDelete == OnDelete.Cascade && modelBOnDelete == OnDelete.Cascade
  lazy val modelA: Model                                     = schema.getModelByName_!(modelAName)
  lazy val modelB: Model                                     = schema.getModelByName_!(modelBName)
  lazy val modelAField: RelationField                        = modelA.relationFields.find(_.isRelationWithNameAndSide(name, RelationSide.A)).get
  lazy val modelBField: RelationField                        = modelB.relationFields.find(_.isRelationWithNameAndSide(name, RelationSide.B)).get
  lazy val hasManifestation: Boolean                         = manifestation.isDefined
  lazy val isInlineRelation: Boolean                         = manifestation.exists(_.isInstanceOf[EmbeddedRelationLink])
  lazy val inlineManifestation: Option[EmbeddedRelationLink] = manifestation.collect { case x: EmbeddedRelationLink => x }

  lazy val relationTableName = manifestation match {
    case Some(m: RelationTable)        => m.table
    case Some(m: EmbeddedRelationLink) => schema.getModelByName_!(m.inTableOfModelName).dbName
    case None                          => "_" + name
  }

  lazy val modelAColumn: String = manifestation match {
    case Some(m: RelationTable)        => m.modelAColumn
    case Some(m: EmbeddedRelationLink) => if (m.inTableOfModelName == modelAName && !isSelfRelation) modelA.idField_!.dbName else m.referencingColumn
    case None                          => "A"
  }

  lazy val modelBColumn: String = manifestation match {
    case Some(m: RelationTable)        => m.modelBColumn
    case Some(m: EmbeddedRelationLink) => if (m.inTableOfModelName == modelBName) modelB.idField_!.dbName else m.referencingColumn
    case None                          => "B"
  }

  lazy val isManyToMany: Boolean = {
    val modelAFieldIsList = modelAField.isList
    val modelBFieldIsList = modelBField.isList
    modelAFieldIsList && modelBFieldIsList
  }

  def columnForRelationSide(relationSide: RelationSide.Value): String = if (relationSide == RelationSide.A) modelAColumn else modelBColumn

  def getFieldOnModel(modelId: String): RelationField = {
    modelId match {
      case `modelAName` => modelAField
      case `modelBName` => modelBField
      case _            => sys.error(s"The model id $modelId is not part of this relation $name")
    }
  }
}
