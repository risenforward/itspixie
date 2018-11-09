package com.prisma.deploy.migration.validation

import com.prisma.shared.models.ApiConnectorCapability.{EmbeddedScalarListsCapability, NonEmbeddedScalarListCapability}
import com.prisma.shared.models.FieldBehaviour.{ScalarListBehaviour, ScalarListStrategy}
import org.scalatest.{Matchers, WordSpecLike}

class ScalarListDirectiveSpec extends WordSpecLike with Matchers with DataModelValidationSpecBase {
  "@scalarList should be optional" in {
    val dataModelString =
      """
        |type Model {
        |  id: ID! @id
        |  tags: [String!]
        |}
      """.stripMargin
    val dataModel = validate(dataModelString, Set(NonEmbeddedScalarListCapability))
    dataModel.type_!("Model").scalarField_!("tags").behaviour should be(Some(ScalarListBehaviour(ScalarListStrategy.Relation)))

    val dataModel2 = validate(dataModelString, Set(EmbeddedScalarListsCapability))
    dataModel2.type_!("Model").scalarField_!("tags").behaviour should be(Some(ScalarListBehaviour(ScalarListStrategy.Embedded)))
  }

  "@scalarList must fail if an invalid argument is provided" in {
    val dataModelString =
      """
        |type Model {
        |  id: ID! @id
        |  tags: [String!] @scalarList(strategy: FOOBAR)
        |}
      """.stripMargin
    val error = validateThatMustError(dataModelString).head
    error.`type` should equal("Model")
    error.field should equal(Some("tags"))
    error.description should include("Valid values for the strategy argument of `@scalarList` are:")
  }
}
