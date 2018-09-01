package com.prisma.api.mutations.nonEmbedded

import com.prisma.IgnoreMongo
import com.prisma.api.ApiSpecBase
import com.prisma.api.connector.ApiConnectorCapability.ScalarListsCapability
import com.prisma.shared.models.Project
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class DeleteScalarListsSpec extends FlatSpec with Matchers with ApiSpecBase {
  override def runOnlyForCapabilities = Set(ScalarListsCapability)

  "A nested delete  mutation" should "also delete ListTable entries" in {

    val project: Project = SchemaDsl.fromString() {
      """type Top {
        | id: ID! @unique
        | name: String! @unique
        | bottom: Bottom
        |}
        |
        |type Bottom {
        | id: ID! @unique
        | name: String! @unique
        | list: [Int!]!
        |}"""
    }

    database.setup(project)

    server.query(
      """mutation {
        |  createTop(
        |    data: { name: "test", bottom: {create: {name: "test2", list: {set: [1,2,3]}} }}
        |  ){
        |    name
        |    bottom{name, list} 
        |  }
        |}
      """,
      project
    )

    val res = server.query("""mutation{updateTop(where:{name:"test" }data: {bottom: {delete: true}}){name, bottom{name}}}""", project)

    res.toString should be("""{"data":{"updateTop":{"name":"test","bottom":null}}}""")
  }

  "A cascading delete  mutation" should "also delete ListTable entries" in {

    val project: Project = SchemaDsl.fromString() {
      """type Top {
        | id: ID! @unique
        | name: String! @unique
        | bottom: Bottom @relation(name: "Test", onDelete: CASCADE)
        |}
        |
        |type Bottom {
        | id: ID! @unique
        | name: String! @unique
        | list: [Int!]!
        | top: Top! @relation(name: "Test")
        |}"""
    }

    database.setup(project)

    server.query(
      """mutation {
        |  createTop(
        |    data: { name: "test", bottom: {create: {name: "test2", list: {set: [1,2,3]}} }}
        |  ){
        |    name
        |    bottom{name, list}
        |  }
        |}
      """,
      project
    )

    server.query("""mutation{deleteTop(where:{name:"test"}){name}}""", project)

    server.query("""query{tops{name}}""", project).toString() should be("""{"data":{"tops":[]}}""")
    server.query("""query{bottoms{name}}""", project).toString() should be("""{"data":{"bottoms":[]}}""")

  }
}
