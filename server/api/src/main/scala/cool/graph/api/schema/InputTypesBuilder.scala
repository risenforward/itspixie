package cool.graph.api.schema

import cool.graph.cache.Cache
import cool.graph.shared.models.{Field, Model, Project, Relation}
import sangria.schema.{InputField, InputObjectType, InputType, ListInputType, OptionInputType}

trait InputTypesBuilder {
  def inputObjectTypeForCreate(model: Model, omitRelation: Option[Relation] = None): InputObjectType[Any]

  def inputObjectTypeForUpdate(model: Model): InputObjectType[Any]

  def inputObjectTypeForWhere(model: Model): InputObjectType[Any]
}

case class CachedInputTypesBuilder(project: Project) extends UncachedInputTypesBuilder(project) {
  import java.lang.{StringBuilder => JStringBuilder}

  val cache = Cache.unbounded[String, InputObjectType[Any]]()

  override def inputObjectTypeForCreate(model: Model, omitRelation: Option[Relation]): InputObjectType[Any] = {
    cache.getOrUpdate(cacheKey("cachedInputObjectTypeForCreate", model, omitRelation), { () =>
      computeInputObjectTypeForCreate(model, omitRelation)
    })
  }

  override def inputObjectTypeForUpdate(model: Model): InputObjectType[Any] = {
    cache.getOrUpdate(cacheKey("cachedInputObjectTypeForUpdate", model), { () =>
      computeInputObjectTypeForUpdate(model)
    })
  }

  private def cacheKey(name: String, model: Model, relation: Option[Relation] = None): String = {
    val sb = new JStringBuilder()
    sb.append(name)
    sb.append(model.id)
    sb.append(relation.orNull)
    sb.toString
  }
}

abstract class UncachedInputTypesBuilder(project: Project) extends InputTypesBuilder {
  override def inputObjectTypeForCreate(model: Model, omitRelation: Option[Relation]): InputObjectType[Any] = {
    computeInputObjectTypeForCreate(model, omitRelation)
  }

  override def inputObjectTypeForUpdate(model: Model): InputObjectType[Any] = {
    computeInputObjectTypeForUpdate(model)
  }

  override def inputObjectTypeForWhere(model: Model): InputObjectType[Any] = {
    computeInputObjectTypeForWhere(model)
  }

  protected def computeInputObjectTypeForCreate(model: Model, omitRelation: Option[Relation]): InputObjectType[Any] = {
    val inputObjectTypeName = omitRelation match {
      case None =>
        s"${model.name}CreateInput"

      case Some(relation) =>
        val field = relation.getField_!(project, model)
        s"${model.name}CreateWithout${field.name.capitalize}Input"
    }

    InputObjectType[Any](
      name = inputObjectTypeName,
      fieldsFn = () => {
        computeScalarInputFieldsForCreate(model) ++ computeRelationalInputFields(model, omitRelation, operation = "Create")
      }
    )
  }

  protected def computeInputObjectTypeForUpdate(model: Model): InputObjectType[Any] = {
    InputObjectType[Any](
      name = s"${model.name}UpdateInput",
      fieldsFn = () => {
        computeScalarInputFieldsForUpdate(model) ++
          computeRelationalInputFields(model, omitRelation = None, operation = "Update")
      }
    )
  }

  protected def computeInputObjectTypeForWhere(model: Model): InputObjectType[Any] = {
    InputObjectType[Any](
      name = s"${model.name}WhereUniqueInput",
      fields = model.fields.filter(_.isUnique).map(field => InputField(name = field.name, fieldType = SchemaBuilderUtils.mapToOptionalInputType(field)))
    )
  }

  private def computeScalarInputFieldsForCreate(model: Model): List[InputField[Any]] = {
    val filteredModel = model.filterFields(_.isWritable)
    computeScalarInputFields(filteredModel, FieldToInputTypeMapper.mapForCreateCase)
  }

  private def computeScalarInputFieldsForUpdate(model: Model): List[InputField[Any]] = {
    val filteredModel = model.filterFields(f => f.isWritable)
    computeScalarInputFields(filteredModel, FieldToInputTypeMapper.mapForUpdateCase)
  }

  private def computeScalarInputFields(model: Model, mapToInputType: Field => InputType[Any]): List[InputField[Any]] = {
    model.scalarFields.map { field =>
      InputField(field.name, mapToInputType(field))
    }
  }

  private def computeRelationalInputFields(model: Model, omitRelation: Option[Relation], operation: String): List[InputField[Any]] = {
    val manyRelationArguments = model.listRelationFields.flatMap { field =>
      val subModel              = field.relatedModel_!(project)
      val relation              = field.relation.get
      val relatedField          = field.relatedFieldEager(project)
      val relationMustBeOmitted = omitRelation.exists(rel => field.isRelationWithId(rel.id))

      if (relationMustBeOmitted) {
        None
      } else {
        val inputObjectType = InputObjectType[Any](
          name = s"${subModel.name}${operation}ManyWithout${relatedField.name.capitalize}Input",
          fieldsFn = () => {
            List(
              InputField("create", OptionInputType(ListInputType(inputObjectTypeForCreate(subModel, Some(relation))))),
              InputField("connect", OptionInputType(ListInputType(inputObjectTypeForWhere(subModel))))
            )
          }
        )
        Some(InputField[Any](field.name, OptionInputType(inputObjectType)))
      }
    }
    val singleRelationArguments = model.singleRelationFields.flatMap { field =>
      val subModel              = field.relatedModel_!(project)
      val relation              = field.relation.get
      val relatedField          = field.relatedFieldEager(project)
      val relationMustBeOmitted = omitRelation.exists(rel => field.isRelationWithId(rel.id))

      if (relationMustBeOmitted) {
        None
      } else {
        val inputObjectType = InputObjectType[Any](
          name = s"${subModel.name}${operation}OneWithout${relatedField.name.capitalize}Input",
          fieldsFn = () => {
            List(
              InputField("create", OptionInputType(inputObjectTypeForCreate(subModel, Some(relation)))),
              InputField("connect", OptionInputType(inputObjectTypeForWhere(subModel)))
            )
          }
        )
        Some(InputField[Any](field.name, OptionInputType(inputObjectType)))
      }
    }
    manyRelationArguments ++ singleRelationArguments
  }
}

object FieldToInputTypeMapper {
  def mapForCreateCase(field: Field): InputType[Any] = field.isRequired && field.defaultValue.isEmpty match {
    case true  => SchemaBuilderUtils.mapToRequiredInputType(field)
    case false => SchemaBuilderUtils.mapToOptionalInputType(field)
  }

  def mapForUpdateCase(field: Field): InputType[Any] = field.name match {
    case "id" => SchemaBuilderUtils.mapToRequiredInputType(field)
    case _    => SchemaBuilderUtils.mapToOptionalInputType(field)
  }
}
