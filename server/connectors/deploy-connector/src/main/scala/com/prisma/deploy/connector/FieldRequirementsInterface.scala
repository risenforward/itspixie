package com.prisma.deploy.connector

import com.prisma.shared.models.FieldTemplate

trait FieldRequirementsInterface {
  val reservedFieldRequirements: Vector[FieldRequirement]
  val requiredReservedFields: Vector[FieldRequirement]
  val hiddenReservedField: Vector[FieldTemplate]
  val isAutogenerated: Boolean
}

case class FieldRequirement(name: String, validTypes: Vector[String], required: Boolean, unique: Boolean, list: Boolean)

object FieldRequirement {
  def apply(name: String, validType: String, required: Boolean, unique: Boolean, list: Boolean): FieldRequirement = {
    FieldRequirement(name = name, validTypes = Vector(validType), required = required, unique = unique, list = list)
  }
}
