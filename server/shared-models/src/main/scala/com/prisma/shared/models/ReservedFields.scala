package com.prisma.shared.models

import com.prisma.shared.models.FieldBehaviour.IdBehaviour

object ReservedFields {
  val idFieldName         = "id"
  val internalIdFieldName = "_id"
  val updatedAtFieldName  = "updatedAt"
  val createdAtFieldName  = "createdAt"
  val reservedFieldNames  = Vector(idFieldName, updatedAtFieldName, createdAtFieldName)

  def reservedFieldFor(name: String): FieldTemplate = {
    name match {
      case x if x == idFieldName        => idField()
      case x if x == createdAtFieldName => createdAtField()
      case x if x == updatedAtFieldName => updatedAtField()
      case _                            => throw new Exception(s"Unknown reserved field: $name")
    }
  }

  private def createdAtField(): FieldTemplate = {
    FieldTemplate(
      name = createdAtFieldName,
      typeIdentifier = TypeIdentifier.DateTime,
      isRequired = true,
      isList = false,
      isUnique = false,
      isHidden = true,
      isReadonly = true,
      enum = None,
      defaultValue = None,
      relationName = None,
      relationSide = None,
      manifestation = None,
      behaviour = None
    )
  }

  private def updatedAtField(): FieldTemplate = {
    FieldTemplate(
      name = updatedAtFieldName,
      typeIdentifier = TypeIdentifier.DateTime,
      isRequired = true,
      isList = false,
      isUnique = false,
      isHidden = true,
      isReadonly = true,
      enum = None,
      defaultValue = None,
      relationName = None,
      relationSide = None,
      manifestation = None,
      behaviour = None
    )
  }

  private def idField(): FieldTemplate = {
    FieldTemplate(
      name = idFieldName,
      typeIdentifier = TypeIdentifier.Cuid,
      isRequired = true,
      isList = false,
      isUnique = true,
      isHidden = true,
      isReadonly = true,
      enum = None,
      defaultValue = None,
      relationName = None,
      relationSide = None,
      manifestation = None,
      behaviour = Some(IdBehaviour(strategy = FieldBehaviour.IdStrategy.Auto))
    )
  }
}
