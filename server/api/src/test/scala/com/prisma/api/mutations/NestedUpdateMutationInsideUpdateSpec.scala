package com.prisma.api.mutations

import com.prisma.api.ApiBaseSpec
import com.prisma.shared.project_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class NestedUpdateMutationInsideUpdateSpec extends FlatSpec with Matchers with ApiBaseSpec {

  "a one to many relation" should "be updateable by id through a nested mutation" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field("text", _.String)
      schema.model("Todo").oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    val createResult = server.executeQuerySimple(
      """mutation {
        |  createTodo(
        |    data: {
        |      comments: {
        |        create: [{text: "comment1"}, {text: "comment2"}]
        |      }
        |    }
        |  ){ 
        |    id 
        |    comments { id }
        |  } 
        |}""".stripMargin,
      project
    )

    val todoId     = createResult.pathAsString("data.createTodo.id")
    val comment1Id = createResult.pathAsString("data.createTodo.comments.[0].id")
    val comment2Id = createResult.pathAsString("data.createTodo.comments.[1].id")

    val result = server.executeQuerySimple(
      s"""mutation {
         |  updateTodo(
         |    where: {
         |      id: "$todoId"
         |    }
         |    data:{
         |      comments: {
         |        update: [
         |          {where: {id: "$comment1Id"}, data: {text: "update comment1"}},
         |          {where: {id: "$comment2Id"}, data: {text: "update comment2"}},
         |        ]
         |      }
         |    }
         |  ){
         |    comments {
         |      text
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    mustBeEqual(result.pathAsString("data.updateTodo.comments.[0].text").toString, """update comment1""")
    mustBeEqual(result.pathAsString("data.updateTodo.comments.[1].text").toString, """update comment2""")
  }

  "a one to many relation" should "be updateable by any unique argument through a nested mutation" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field("text", _.String).field_!("alias", _.String, isUnique = true)
      schema.model("Todo").oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    val createResult = server.executeQuerySimple(
      """mutation {
        |  createTodo(
        |    data: {
        |      comments: {
        |        create: [{text: "comment1", alias: "alias1"}, {text: "comment2", alias: "alias2"}]
        |      }
        |    }
        |  ){
        |    id
        |    comments { id }
        |  }
        |}""".stripMargin,
      project
    )
    val todoId = createResult.pathAsString("data.createTodo.id")

    val result = server.executeQuerySimple(
      s"""mutation {
         |  updateTodo(
         |    where: {
         |      id: "$todoId"
         |    }
         |    data:{
         |      comments: {
         |        update: [
         |          {where: {alias: "alias1"}, data: {text: "update comment1"}},
         |          {where: {alias: "alias2"}, data: {text: "update comment2"}}
         |        ]
         |      }
         |    }
         |  ){
         |    comments {
         |      text
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    mustBeEqual(result.pathAsString("data.updateTodo.comments.[0].text").toString, """update comment1""")
    mustBeEqual(result.pathAsString("data.updateTodo.comments.[1].text").toString, """update comment2""")
  }

  "a many to one relation" should "be updateable by id through a nested mutation" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field("text", _.String)
      schema.model("Todo").field("title", _.String).oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    val createResult = server.executeQuerySimple(
      """mutation {
        |  createTodo(
        |    data: {
        |      comments: {
        |        create: [{text: "comment1"}]
        |      }
        |    }
        |  ){
        |    id
        |    comments { id }
        |  }
        |}""".stripMargin,
      project
    )
    val todoId    = createResult.pathAsString("data.createTodo.id")
    val commentId = createResult.pathAsString("data.createTodo.comments.[0].id")

    val result = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateComment(
         |    where: {
         |      id: "$commentId"
         |    }
         |    data: {
         |      todo: {
         |        update: {where: {id: "$todoId"}, data: {title: "updated title"}}
         |      }
         |    }
         |  ){
         |    todo {
         |      title
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )
    mustBeEqual(result.pathAsJsValue("data.updateComment.todo").toString, """{"title":"updated title"}""")
  }

  "a one to one relation" should "be updateable by id through a nested mutation" in {
    val project = SchemaDsl() { schema =>
      val note = schema.model("Note").field("text", _.String)
      schema.model("Todo").field_!("title", _.String).oneToOneRelation("note", "todo", note)
    }
    database.setup(project)

    val createResult = server.executeQuerySimple(
      """mutation { 
        |  createNote(
        |    data: {
        |      todo: {
        |        create: { title: "the title" }
        |      }
        |    }
        |  ){ 
        |    id
        |    todo { id }
        |  } 
        |}""".stripMargin,
      project
    )
    val noteId = createResult.pathAsString("data.createNote.id")
    val todoId = createResult.pathAsString("data.createNote.todo.id")

    val result = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateNote(
         |    where: {
         |      id: "$noteId"
         |    }
         |    data: {
         |      todo: {
         |        update: { where: {id: "$todoId"}, data:{title: "updated title"} }
         |      }
         |    }
         |  ){
         |    todo {
         |      title
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )
    mustBeEqual(result.pathAsJsValue("data.updateNote.todo").toString, """{"title":"updated title"}""")
  }

  "a one to one relation" should "fail gracefully on wrong where and assign error correctly and not execute partially" in {
    val project = SchemaDsl() { schema =>
      val note = schema.model("Note").field("text", _.String)
      schema.model("Todo").field_!("title", _.String).oneToOneRelation("note", "todo", note)
    }
    database.setup(project)

    val createResult = server.executeQuerySimple(
      """mutation {
        |  createNote(
        |    data: {
        |      text: "Some Text"
        |      todo: {
        |        create: { title: "the title" }
        |      }
        |    }
        |  ){
        |    id
        |    todo { id }
        |  }
        |}""".stripMargin,
      project
    )
    val noteId = createResult.pathAsString("data.createNote.id")
    val todoId = createResult.pathAsString("data.createNote.todo.id")

    server.executeQuerySimpleThatMustFail(
      s"""
         |mutation {
         |  updateNote(
         |    where: {
         |      id: "$noteId"
         |    }
         |    data: {
         |      text: "Some Changed Text"
         |      todo: {
         |        update: {
         |          where: {id: "DOES NOT EXIST"},
         |          data:{title: "updated title"}
         |        }
         |      }
         |    }
         |  ){
         |    text
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3039,
      errorContains = "No Node for the model Todo with value DOES NOT EXIST for id found."
    )

    server.executeQuerySimple(s"""query{note(where:{id: "$noteId"}){text}}""", project, dataContains = """{"note":{"text":"Some Text"}}""")
    server.executeQuerySimple(s"""query{todo(where:{id: "$todoId"}){title}}""", project, dataContains = """{"todo":{"title":"the title"}}""")
  }

  "a many to many relation" should "handle null in unique fields" in {
    val project = SchemaDsl() { schema =>
      val note = schema.model("Note").field("text", _.String, isUnique = true)
      schema.model("Todo").field_!("title", _.String, isUnique = true).field("unique", _.String, isUnique = true).manyToManyRelation("notes", "todos", note)
    }
    database.setup(project)

    val createResult = server.executeQuerySimple(
      """mutation {
        |  createNote(
        |    data: {
        |      text: "Some Text"
        |      todos:
        |      {
        |       create: [{ title: "the title", unique: "test"},{ title: "the other title"}]
        |      }
        |    }
        |  ){
        |    id
        |    todos { id }
        |  }
        |}""".stripMargin,
      project
    )

    val result = server.executeQuerySimpleThatMustFail(
      s"""
         |mutation {
         |  updateNote(
         |    where: {
         |      text: "Some Text"
         |    }
         |    data: {
         |      text: "Some Changed Text"
         |      todos: {
         |        update: {
         |          where: {unique: null},
         |          data:{title: "updated title"}
         |        }
         |      }
         |    }
         |  ){
         |    text
         |    todos {
         |      title
         |    }
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3040,
      errorContains = "You provided an invalid argument for the where selector on Todo."
    )
  }

  "a deeply nested mutation" should "execute all levels of the mutation" in {
    val project = SchemaDsl() { schema =>
      val list = schema.model("List").field_!("name", _.String)
      val todo = schema.model("Todo").field_!("title", _.String)
      val tag  = schema.model("Tag").field_!("name", _.String)

      list.oneToManyRelation("todos", "list", todo)
      todo.oneToOneRelation("tag", "todo", tag)
    }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createList(data: {
        |    name: "the list",
        |    todos: {
        |      create: [
        |        {
        |          title: "the todo"
        |          tag: {
        |            create: {
        |              name: "the tag"
        |            }
        |          }
        |        }
        |      ]
        |    }
        |  }) {
        |    id
        |    todos {
        |      id
        |      tag {
        |        id
        |      }
        |    }
        |  }
        |}
      """.stripMargin

    val createResult = server.executeQuerySimple(createMutation, project)
    val listId       = createResult.pathAsString("data.createList.id")
    val todoId       = createResult.pathAsString("data.createList.todos.[0].id")
    val tagId        = createResult.pathAsString("data.createList.todos.[0].tag.id")

    val updateMutation =
      s"""
        |mutation  {
        |  updateList(
        |    where: {
        |      id: "$listId"
        |    }
        |    data: {
        |      name: "updated list",
        |      todos: {
        |        update: [
        |          {
        |            where: {
        |              id: "$todoId"
        |            }
        |            data: {
        |              title: "updated todo"
        |              tag: {
        |                update: {
        |                  where: {
        |                    id: "$tagId"
        |                  }
        |                  data: {
        |                    name: "updated tag"
        |                  }
        |                }
        |              }
        |            }
        |          }
        |        ]
        |      }
        |    }
        |  ) {
        |    name
        |    todos {
        |      title
        |      tag {
        |        name
        |      }
        |    }
        |  }
        |}
      """.stripMargin

    val result = server.executeQuerySimple(updateMutation, project)
    result.pathAsString("data.updateList.name") should equal("updated list")
    result.pathAsString("data.updateList.todos.[0].title") should equal("updated todo")
    result.pathAsString("data.updateList.todos.[0].tag.name") should equal("updated tag")
  }
}
