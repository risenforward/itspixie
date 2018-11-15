package com.prisma.api.mutations.nonEmbedded

import com.prisma.api.ApiSpecBase
import com.prisma.shared.models.ApiConnectorCapability.JoinRelationLinksCapability
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class NonEmbeddedUpdatedAtShouldChangeSpec extends FlatSpec with Matchers with ApiSpecBase {
  override def runOnlyForCapabilities = Set(JoinRelationLinksCapability)

  val project = SchemaDsl.fromString() {

    """type Top {
      |id: ID! @unique
      |top: String! @unique
      |bottom: Bottom
      |createdAt: DateTime!
      |updatedAt: DateTime!
      |}
      |
      |type Bottom {
      |id: ID! @unique
      |bottom: String! @unique
      |top: Top
      |createdAt: DateTime!
      |updatedAt: DateTime!
      |}
      |"""
  }

  database.setup(project)

  "Updating a nested data item" should "change it's updatedAt value" in {
    val updatedAt = server
      .query("""mutation a {createTop(data: { top: "top2", bottom: {create:{bottom: "Bottom2"}} }) {bottom{updatedAt}}}""", project)
      .pathAsString("data.createTop.bottom.updatedAt")

    val changedUpdatedAt = server
      .query(
        s"""mutation b {
           |  updateTop(
           |    where: { top: "top2" }
           |    data: { bottom: {update:{bottom: "bottom20"}} }
           |  ) {
           |    bottom{
           |      updatedAt
           |    }
           |  }
           |}
      """,
        project
      )
      .pathAsString("data.updateTop.bottom.updatedAt")

    updatedAt should not equal changedUpdatedAt
  }

  "Upserting a nested data item" should "change it's updatedAt value" in {
    val updatedAt = server
      .query("""mutation a {createTop(data: { top: "top4", bottom: {create:{bottom: "Bottom4"}} }) {bottom{updatedAt}}}""", project)
      .pathAsString("data.createTop.bottom.updatedAt")

    val changedUpdatedAt = server
      .query(
        s"""mutation b {
           |  updateTop(
           |    where: { top: "top4" }
           |    data: { bottom: {upsert:{create:{bottom: "Should not matter"}, update:{bottom: "Bottom40"}}} }
           |  ) {
           |    bottom{
           |      updatedAt
           |    }
           |  }
           |}
      """,
        project
      )
      .pathAsString("data.updateTop.bottom.updatedAt")

    updatedAt should not equal changedUpdatedAt
  }
}
