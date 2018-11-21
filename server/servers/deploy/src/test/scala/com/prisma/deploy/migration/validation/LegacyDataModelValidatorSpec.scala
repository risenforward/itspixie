package com.prisma.deploy.migration.validation

import com.prisma.deploy.connector.{FieldRequirement, FieldRequirementsInterface}
import com.prisma.deploy.specutils.DeploySpecBase
import com.prisma.shared.models.ConnectorCapability.MigrationsCapability
import com.prisma.shared.models.{ConnectorCapabilities, ConnectorCapability, FieldTemplate}
import org.scalatest.{Matchers, WordSpecLike}

import scala.collection.immutable.Seq

class LegacyDataModelValidatorSpec extends WordSpecLike with Matchers with DeploySpecBase {

  case class FieldRequirementImpl(
      isActive: Boolean
  ) extends FieldRequirementsInterface {
    val idFieldRequirementForPassive = Vector(FieldRequirement("id", Vector("ID", "UUID", "Int"), required = true, unique = true, list = false))
    val idFieldRequirementForActive  = Vector(FieldRequirement("id", Vector("ID", "UUID"), required = true, unique = true, list = false))

    val baseReservedFieldsRequirements = Vector(
      FieldRequirement("updatedAt", "DateTime", required = true, unique = false, list = false),
      FieldRequirement("createdAt", "DateTime", required = true, unique = false, list = false)
    )

    val reservedFieldsRequirementsForActive  = baseReservedFieldsRequirements ++ idFieldRequirementForActive
    val reservedFieldsRequirementsForPassive = baseReservedFieldsRequirements ++ idFieldRequirementForPassive

    val reservedFieldRequirements: Vector[FieldRequirement] = if (isActive) reservedFieldsRequirementsForActive else reservedFieldsRequirementsForPassive
    val requiredReservedFields: Vector[FieldRequirement]    = if (isActive) Vector.empty else idFieldRequirementForPassive
    val hiddenReservedField: Vector[FieldTemplate]          = Vector.empty
    val isAutogenerated: Boolean                            = false
  }

  "succeed if the schema is fine" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |}
      """.stripMargin
    validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability)) should be(empty)
  }

  "fail if the schema is syntactically incorrect" in {
    val dataModelString =
      """
        |type Todo  {
        |  title: String
        |  isDone
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(1))
    result.head.`type` should equal("Global")
  }

  "fail if the schema is missing a type" in {
    // the relation directive is there as this used to cause an exception
    val dataModelString =
      """
        |type Todo  {
        |  title: String
        |  owner: User @relation(name: "Test", onDelete: CASCADE)
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(1))
    result.head.`type` should equal("Todo")
    result.head.field should equal(Some("owner"))
    result.head.description should equal("The field `owner` has the type `User` but there's no type or enum declaration with that name.")
  }

  "succeed if an unambiguous relation field does not specify the relation directive" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments: [Comment]
        |}
        |
        |type Comment {
        |  text: String
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(0))
  }

  "fail if ambiguous relation fields do not specify the relation directive" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments: [Comment]
        |  comments2: [Comment]
        |}
        |
        |type Comment {
        |  text: String
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(2))

    result.head.`type` should equal("Todo")
    result.head.field should equal(Some("comments"))
    result.head.description should include("The relation field `comments` must specify a `@relation` directive")

    result(1).`type` should equal("Todo")
    result(1).field should equal(Some("comments2"))
    result(1).description should include("The relation field `comments2` must specify a `@relation` directive")
  }

  "fail if ambiguous relation fields specify the same relation name" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments: [Comment] @relation(name: "TodoToComments")
        |  comments2: [Comment] @relation(name: "TodoToComments")
        |}
        |
        |type Comment {
        |  todo: Todo! @relation(name: "TodoToComments")
        |  todo2: Todo! @relation(name: "TodoToComments")
        |  text: String
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(4))
    result.forall(_.description.contains("A relation directive cannot appear more than twice.")) should be(true)
  }

  // TODO: the backwards field should not be required here.
  "succeed if ambiguous relation fields specify the relation directive" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments: [Comment] @relation(name: "TodoToComments1")
        |  comments2: [Comment] @relation(name: "TodoToComments2")
        |}
        |
        |type Comment {
        |  todo: Todo! @relation(name: "TodoToComments1")
        |  todo2: Todo! @relation(name: "TodoToComments2")
        |  text: String
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(0))
  }

  "fail if a relation directive appears on a scalar field" in {
    val dataModelString =
      """
        |type Todo  {
        |  title: String @relation(name: "TodoToComments")
        |}
        |
        |type Comment {
        |  bla: String
        |}
        """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(1))
    result.head.`type` should equal("Todo")
    result.head.field should equal(Some("title"))
    result.head.description should include("cannot specify the `@relation` directive.")
  }

  "succeed if a relation name specifies the relation directive only once" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments: [Comment] @relation(name: "TodoToComments")
        |}
        |
        |type Comment {
        |  bla: String
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(0))
  }

  "succeed if a relation directive specifies a valid onDelete attribute" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments1: [Comment1] @relation(name: "TodoToComments1", onDelete: CASCADE)
        |  comments2: [Comment2] @relation(name: "TodoToComments2", onDelete: SET_NULL)
        |  comments3: [Comment3] @relation(name: "TodoToComments3")
        |}
        |
        |type Comment1 {
        |  bla: String
        |}
        |type Comment2 {
        |  bla: String
        |}
        |type Comment3 {
        |  bla: String
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(0))
  }

  "fail if a relation directive specifies an invalid onDelete attribute" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments: [Comment] @relation(name: "TodoToComments", onDelete: INVALID)
        |}
        |
        |type Comment {
        |  bla: String
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(1))
    result.head.description should include("not a valid value for onDelete")
  }

  // TODO: adapt
  "succeed if a relation gets renamed" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments: [Comment] @relation(name: "TodoToCommentsNew", oldName: "TodoToComments")
        |}
        |
        |type Comment {
        |  bla: String
        |  todo: Todo @relation(name: "TodoToComments")
        |}
      """.stripMargin

    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(0))
  }

  // TODO: adapt
  "succeed if a one field self relation does appear only once" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  todo: Todo @relation(name: "OneFieldSelfRelation")
        |  todos: [Todo] @relation(name: "OneFieldManySelfRelation")
        |}
      """.stripMargin

    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(0))
  }

  "fail if the relation directive does not appear on the right fields case 1" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments: [Comment] @relation(name: "TodoToComments")
        |}
        |
        |type Comment {
        |  bla: String
        |}
        |
        |type Author {
        |  name: String
        |  todo: Todo @relation(name: "TodoToComments")
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(1))
    val first = result.head
    first.`type` should equal("Todo")
    first.field should equal(Some("comments"))
    first.description should include("But the other directive for this relation appeared on the type")
  }

  "fail if the relation directive does not appear on the right fields case 2" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments: [Comment] @relation(name: "TodoToComments")
        |}
        |
        |type Comment {
        |  bla: String
        |}
        |
        |type Author {
        |  name: String
        |  whatever: Comment @relation(name: "TodoToComments")
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(2))
    val first = result.head
    first.`type` should equal("Todo")
    first.field should equal(Some("comments"))
    first.description should include("But the other directive for this relation appeared on the type")

    val second = result(1)
    second.`type` should equal("Author")
    second.field should equal(Some("whatever"))
    second.description should include("But the other directive for this relation appeared on the type")
  }

  // TODO: we are in the process of changing the valid list field syntax and allow all notations for now.
  "not accept that a many relation field is not marked as required" ignore {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments: [Comment!] @relation(name: "TodoToComments")
        |}
        |
        |type Comment {
        |  text: String
        |  todo: Todo @relation(name: "TodoToComments")
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(1))
  }

  "succeed if a one relation field is marked as required" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments: [Comment] @relation(name: "TodoToComments")
        |}
        |
        |type Comment {
        |  text: String
        |  todo: Todo! @relation(name: "TodoToComments")
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(0))
  }

  "fail if schema refers to a type that is not there" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |  comments: [Comment]
        |}
      """.stripMargin

    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(1))
    val error = result.head
    error.`type` should equal("Todo")
    error.field should equal(Some("comments"))
    error.description should include("no type or enum declaration with that name")
  }

  "NOT fail if the directives contain all required attributes" in {
    val directiveRequirements = Seq(
      DirectiveRequirement("zero", Seq.empty, Seq.empty),
      DirectiveRequirement("one", Seq(RequiredArg("a", mustBeAString = true)), Seq.empty),
      DirectiveRequirement("two", Seq(RequiredArg("a", mustBeAString = false), RequiredArg("b", mustBeAString = true)), Seq.empty)
    )
    val dataModelString =
      """
        |type Todo {
        |  title: String @zero @one(a: "") @two(a:1, b: "")
        |}
      """.stripMargin

    val result = validate(
      dataModelString,
      isActive = true,
      Set(MigrationsCapability),
      directiveRequirements
    )
    result should have(size(0))
  }

  "fail if a directive misses a required attribute" in {
    val directiveRequirements = Seq(
      DirectiveRequirement("one", Seq(RequiredArg("a", mustBeAString = true)), Seq.empty),
      DirectiveRequirement("two", Seq(RequiredArg("a", mustBeAString = false), RequiredArg("b", mustBeAString = true)), Seq.empty)
    )
    val dataModelString =
      """
        |type Todo {
        |  title: String @one(a:1) @two(a:1)
        |}
      """.stripMargin

    val result = validate(
      dataModelString,
      isActive = true,
      Set(MigrationsCapability),
      directiveRequirements
    )
    result should have(size(2))
    val error1 = result.head
    error1.`type` should equal("Todo")
    error1.field should equal(Some("title"))
    error1.description should include(missingDirectiveArgument("one", "a"))

    val error2 = result(1)
    error2.`type` should equal("Todo")
    error2.field should equal(Some("title"))
    error2.description should include(missingDirectiveArgument("two", "b"))
  }

  "fail if the values in an enum declaration don't begin uppercase" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String @one @two(a:"")
        |  status: TodoStatus
        |}
        |enum TodoStatus {
        |  active
        |  done
        |}
      """.stripMargin

    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(1))
    val error1 = result.head
    error1.`type` should equal("TodoStatus")
    error1.field should equal(None)
    error1.description should include("uppercase")
  }

  "fail if the values in an enum declaration don't pass the validation" in {
    val longEnumValue = "A" * 192
    val dataModelString =
      s"""
         |type Todo {
         |  title: String @one @two(a:"")
         |  status: TodoStatus
         |}
         |enum TodoStatus {
         |  $longEnumValue
         |}
      """.stripMargin

    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(1))
    val error1 = result.head
    error1.`type` should equal("TodoStatus")
    error1.field should equal(None)
    error1.description should include(s"$longEnumValue")
  }

  "fail if a directive appears more than once on a field" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String @default(value: "foo") @default(value: "bar")
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(1))
    val error1 = result.head
    error1.`type` should equal("Todo")
    error1.field should equal(Some("title"))
    error1.description should include(s"Directives must appear exactly once on a field.")
  }

  "fail if the old defaultValue directive appears on a field" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String @defaultValue(value: "foo")
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(1))
    val error1 = result.head
    error1.`type` should equal("Todo")
    error1.field should equal(Some("title"))
    error1.description should include(
      s"""You are using a '@defaultValue' directive. Prisma uses '@default(value: "Value as String")' to declare default values.""")
  }

  "fail if an id field does not specify @unique directive" in {
    val dataModelString =
      """
        |type Todo {
        |  id: ID!
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(1))
    val error1 = result.head
    error1.`type` should equal("Todo")
    error1.field should equal(Some("id"))
    error1.description should include(s"The field `id` is reserved and has to have the format: id: ID! @unique or id: UUID! @unique.")
    //Fixme validate based on fieldRequirements
//        isActive = true.reservedFieldRequirements.find()
  }

  "fail if an id field does not match the valid types for a passive connector" in {
    val dataModelString =
      """
        |type Todo {
        |  id: Float
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = false, capabilities = Set.empty)
    val error1 = result.head
    error1.`type` should equal("Todo")
    error1.field should equal(Some("id"))
    error1.description should include(s"The field `id` is reserved and has to have the format: id: ID! @unique or id: UUID! @unique or id: Int! @unique.")
  }

  "not fail if an id field is of type Int for a passive connector" in {
    val dataModelString =
      """
        |type Todo {
        |  id: Int! @unique
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = false, capabilities = Set.empty)
    result should have(size(0))
  }

  "not fail if a model does not specify an id field at all for an active connector" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, capabilities = Set(MigrationsCapability))
    result should have(size(0))
  }

  "fail if a model does not specify an id field at all for a passive connector" in {
    val dataModelString =
      """
        |type Todo {
        |  title: String
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = false, capabilities = Set.empty)
    val error1 = result.head
    error1.`type` should equal("Todo")
    error1.field should equal(Some("id"))
    error1.description should include(
      s"The required field `id` is missing and has to have the format: id: ID! @unique or id: UUID! @unique or id: Int! @unique.")
  }

  "fail if there is a duplicate enum in datamodel" in {
    val dataModelString =
      """
        |type Todo {
        |  id: ID! @unique
        |}
        |
        |enum Privacy {
        |  A
        |  B
        |}
        |
        |enum Privacy {
        |  C
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, Set(MigrationsCapability))
    result should have(size(1))
    val error1 = result.head
    error1.`type` should equal("Privacy")
    error1.field should equal(None)
    error1.description should include(s"The enum type `Privacy` is defined twice in the schema. Enum names must be unique.")
  }

  "fail if there are duplicate fields in a type" in {
    val dataModelString =
      """
        |type Todo {
        |  id: ID! @unique
        |  title: String!
        |  TITLE: String!
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, Set(MigrationsCapability))
    result should have(size(1))
    val error1 = result.head
    error1.`type` should equal("Todo")
    error1.field should equal(Some("title"))
    error1.description should include(s"The type `Todo` has a duplicate fieldName. The detection of duplicates is performed case insensitive.")
  }

  "fail if there are duplicate types" in {
    val dataModelString =
      """
        |type Todo {
        |  id: ID! @unique
        |}
        |
        |type TODO {
        |  id: ID! @unique
        |}
      """.stripMargin
    val result = validate(dataModelString, isActive = true, Set(MigrationsCapability))
    println(result)
    val error1 = result.head
    error1.`type` should equal("Todo")
    error1.field should equal(None)
    error1.description should include(s"The name of the type `Todo` occurs more than once. The detection of duplicates is performed case insensitive.")
  }

  def validate(
      dataModel: String,
      isActive: Boolean,
      capabilities: Set[ConnectorCapability],
      directiveRequirements: Seq[DirectiveRequirement] = Vector.empty
  ): Seq[DeployError] = {
    LegacyDataModelValidator(
      dataModel,
      directiveRequirements,
      FieldRequirementImpl(isActive = isActive),
      ConnectorCapabilities(MigrationsCapability)
    ).validate
  }

  def missingDirectiveArgument(directive: String, argument: String) = {
    s"the directive `@$directive` but it's missing the required argument `$argument`"
  }
}
