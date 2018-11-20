package com.prisma.deploy.migration.validation.directives

import com.prisma.deploy.migration.DataSchemaAstExtensions._
import com.prisma.deploy.migration.validation.{DeployError, PrismaSdl}
import com.prisma.shared.models.ConnectorCapability
import com.prisma.utils.boolean.BooleanUtils
import sangria.ast._

trait DirectiveBase extends BooleanUtils with SharedDirectiveValidation {
  def name: String

  def postValidate(dataModel: PrismaSdl, capabilities: Set[ConnectorCapability]): Vector[DeployError] = Vector.empty
}

object TypeDirective {
  val all = Vector(TypeDbDirective, EmbeddedDirective)
}

trait TypeDirective[T] extends DirectiveBase {
  def validate(
      document: Document,
      typeDef: ObjectTypeDefinition,
      directive: sangria.ast.Directive,
      capabilities: Set[ConnectorCapability]
  ): Vector[DeployError]

  def value(
      document: Document,
      typeDef: ObjectTypeDefinition,
      capabilities: Set[ConnectorCapability]
  ): Option[T]
}

trait FieldDirective[T] extends DirectiveBase {
  def requiredArgs(capabilities: Set[ConnectorCapability]): Vector[DirectiveArgument[_]]
  def optionalArgs(capabilities: Set[ConnectorCapability]): Vector[DirectiveArgument[_]]

  // gets called if the directive was found. Can return an error message
  def validate(
      document: Document,
      typeDef: ObjectTypeDefinition,
      fieldDef: FieldDefinition,
      directive: sangria.ast.Directive,
      capabilities: Set[ConnectorCapability]
  ): Vector[DeployError]

  def value(
      document: Document,
      typeDef: ObjectTypeDefinition,
      fieldDef: FieldDefinition,
      capabilities: Set[ConnectorCapability]
  ): Option[T]
}

object FieldDirective {
  val behaviour = Vector(IdDirective, CreatedAtDirective, UpdatedAtDirective, ScalarListDirective)
  val all       = Vector(DefaultDirective, RelationDirective, UniqueDirective, FieldDbDirective) ++ behaviour
}

case class ArgumentRequirement(name: String, validate: sangria.ast.Value => Option[String])

trait DirectiveArgument[T] extends SharedDirectiveValidation with BooleanUtils {
  def name: String
  def validate(value: sangria.ast.Value): Option[String]
  def value(directive: Directive): Option[T] = directive.argument(name).map(arg => value(arg.value))
  def value(value: sangria.ast.Value): T
}

object DirectiveArgument extends SharedDirectiveValidation {
  def apply[T](name: String, validate: sangria.ast.Value => Option[String], value: sangria.ast.Value => T) = {
    val (nameArg, validateArg, valueArg) = (name, validate, value)
    new DirectiveArgument[T] {
      override def name                   = nameArg
      override def value(value: Value)    = valueArg(value)
      override def validate(value: Value) = validateArg(value)
    }
  }

  def string(name: String): DirectiveArgument[String] = DirectiveArgument(name, validateStringValue, _.asString)
}
