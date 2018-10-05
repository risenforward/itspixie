package com.prisma.api.mutations

import java.util.UUID

import com.prisma.{IgnoreMongo, IgnoreMySql}
import com.prisma.api.ApiSpecBase
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class UUIDCreateGraphQLSpec extends FlatSpec with Matchers with ApiSpecBase {

  "Creating an item with an id field of type UUID" should "work" taggedAs (IgnoreMySql, IgnoreMongo) in {
    val project = SchemaDsl.fromString() {
      s"""
         |type Todo {
         |  id: UUID! @unique
         |  title: String!
         |}
       """.stripMargin
    }
    database.setup(project)

    val result = server.query(
      """
        |mutation {
        |  createTodo(data: { title: "the title" }){
        |    id
        |    title
        |  }
        |}
      """.stripMargin,
      project
    )

    result.pathAsString("data.createTodo.title") should equal("the title")
    val theUUID = result.pathAsString("data.createTodo.id")
    UUID.fromString(theUUID) // should just not blow up
  }
}
