package com.prisma.api.queries

import com.prisma.api.ApiSpecBase
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class OrderBySpec extends FlatSpec with Matchers with ApiSpecBase {

  val project = SchemaDsl.fromString() {
    """
      |type List {
      |  id: ID! @unique
      |  name: String! @unique
      |  todos: [Todo!]!
      |}
      |
      |type Todo {
      |  id: ID! @unique
      |  title: String! @unique
      |  lists: [List!]!
      |}
    """
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    database.setup(project)
    createLists()
    createTodos()
  }

  "The order when not using order by" should "be the same no matter if pagination is used or not" in {
    val result1 = server.query(
      """
        |{
        |  list(where: {name: "1"}) {
        |    name
        |    todos{
        |      title
        |    }
        |  }
        |}
      """,
      project
    )

    val result2 = server.query(
      """
        |{
        |  list(where: {name: "1"}) {
        |    name
        |    todos(first:10){
        |      title
        |    }
        |  }
        |}
      """,
      project
    )

    result1 should be(result2)

    val result3 = server.query(
      """
        |{
        |  todo(where: {title: "1"}) {
        |    title
        |    lists{
        |      name
        |    }
        |  }
        |}
      """,
      project
    )

    val result4 = server.query(
      """
        |{
        |  todo(where: {title: "1"}) {
        |    title
        |    lists(first:10){
        |      name
        |    }
        |  }
        |}
      """,
      project
    )

    result3 should be(result4)
  }

  private def createLists(): Unit = {
    server.query(
      """
        |mutation {
        |  a: createList(data: {name: "1"}){ id }
        |  b: createList(data: {name: "2"}){ id }
        |  d: createList(data: {name: "4"}){ id }
        |  f: createList(data: {name: "6"}){ id }
        |  g: createList(data: {name: "7"}){ id }
        |  c: createList(data: {name: "3"}){ id }
        |  e: createList(data: {name: "5"}){ id }
        |}
      """,
      project
    )
  }

  private def createTodos(): Unit = {
    server.query(
      """
        |mutation {
        |  a: createTodo(data: {title: "1", lists: { connect: [{name:"1"},{name:"2"},{name:"3"}]}}){ id }
        |  c: createTodo(data: {title: "3", lists: { connect: [{name:"1"},{name:"2"},{name:"3"}]}}){ id }
        |  d: createTodo(data: {title: "4", lists: { connect: [{name:"1"},{name:"2"},{name:"3"}]}}){ id }
        |  f: createTodo(data: {title: "6", lists: { connect: [{name:"1"},{name:"2"},{name:"3"}]}}){ id }
        |  g: createTodo(data: {title: "7", lists: { connect: [{name:"1"},{name:"2"},{name:"3"}]}}){ id }
        |  b: createTodo(data: {title: "2", lists: { connect: [{name:"1"},{name:"2"},{name:"3"}]}}){ id }
        |  e: createTodo(data: {title: "5", lists: { connect: [{name:"1"},{name:"2"},{name:"3"}]}}){ id }
        |}
      """,
      project
    )
  }
}
