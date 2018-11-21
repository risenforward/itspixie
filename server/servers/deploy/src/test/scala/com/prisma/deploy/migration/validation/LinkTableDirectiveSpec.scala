package com.prisma.deploy.migration.validation

import com.prisma.shared.models.ConnectorCapability.RelationLinkTableCapability
import org.scalatest.{Matchers, WordSpecLike}

class LinkTableDirectiveSpec extends WordSpecLike with Matchers with DataModelValidationSpecBase {
  "it must be parsed correctly" in {
    val dataModelString =
      """
        |type Model {
        |  id: ID! @id
        |  model: Model @relation(name: "ModelToModelRelation")
        |}
        |
        |type ModelToModelRelation @linkTable {
        |  A: Model!
        |  B: Model!
        |}
      """.stripMargin

    val dataModel         = validate(dataModelString, Set(RelationLinkTableCapability))
    val relationTableType = dataModel.type_!("ModelToModelRelation")
    relationTableType.isRelationTable should be(true)
  }

  "should error if link tables are not supported" in {
    val dataModelString =
      """
        |type Model {
        |  id: ID! @id
        |  model: Model @relation(name: "ModelToModelRelation")
        |}
        |
        |type ModelToModelRelation @linkTable {
        |  A: Model!
        |  B: Model!
        |}
      """.stripMargin

    val errors = validateThatMustError(dataModelString)
    errors should have(size(1))
    val error = errors.head
    error.`type` should be("ModelToModelRelation")
    error.field should be(None)
    error.description should be("The directive `@linkTable` is not supported by this connector.")
  }

  "should error if the name of the link table is not referred to from any relation" in {
    val dataModelString =
      """
        |type Model {
        |  id: ID! @id
        |  model: Model
        |}
        |
        |type MyLinkTable @linkTable {
        |  A: Model!
        |  B: Model!
        |}
      """.stripMargin

    val errors = validateThatMustError(dataModelString, Set(RelationLinkTableCapability))
    errors should have(size(1))
    val error = errors.head
    error.`type` should be("MyLinkTable")
    error.field should be(None)
    error.description should be("The link table `MyLinkTable` is not referenced in any relation field.")
  }

  "should error if it is referred to in a relation field" ignore {
    // TODO: due to our other checks it seems impossible to actually do this error
    val dataModelString =
      """
        |type Model {
        |  id: ID! @id
        |  field: [ModelToModelRelation] @relation(name: "ModelToModelRelation")
        |}
        |
        |type Other {
        |  id: ID! @id
        |  model: Model @relation(name: "ModelToModelRelation")
        |}
        |
        |type ModelToModelRelation @linkTable {
        |  A: Model!
        |  B: Model!
        |}
      """.stripMargin

    val errors = validateThatMustError(dataModelString, Set(RelationLinkTableCapability))
    println(errors)
    errors should have(size(1))
    val error = errors.head
    error.`type` should be("Model")
    error.field should be(Some("field"))
    error.description should be("asjdfklsjdf")
  }

  "should error if the link table does not refer to the right types" in {
    val dataModelString =
      """
        |type Model {
        |  id: ID! @id
        |  other: Other @relation(name: "MyRelation", link: INLINE)
        |}
        |
        |type Other {
        |  id: ID! @id
        |  model: Model @relation(name: "MyRelation")
        |}
        |
        |type MyRelation @linkTable {
        |  A: Model!
        |  B: Model!
        |}
      """.stripMargin

    val errors = validateThatMustError(dataModelString, Set(RelationLinkTableCapability))
    println(errors)
    errors should have(size(1))
    val error = errors.head
    error.`type` should be("MyRelation")
    error.field should be(None)
    error.description should be("The link table `MyRelation` is not referencing the right types.")
  }
}
