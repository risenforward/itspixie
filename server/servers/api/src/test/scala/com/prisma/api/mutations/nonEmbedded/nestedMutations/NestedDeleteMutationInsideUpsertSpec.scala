package com.prisma.api.mutations.nonEmbedded.nestedMutations

import com.prisma.api.ApiSpecBase
import com.prisma.shared.models.ApiConnectorCapability.JoinRelationsCapability
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class NestedDeleteMutationInsideUpsertSpec extends FlatSpec with Matchers with ApiSpecBase with NestedMutationBase {
  override def runOnlyForCapabilities = Set(JoinRelationsCapability)

  "a P1! to C1! relation " should "error when deleting the child" in {
    val project = SchemaDsl.fromString() { schemaP1reqToC1req }
    database.setup(project)

    val res = server
      .query(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childReq: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    id
          |    childReq{
          |       id
          |    }
          |  }
          |}""",
        project
      )
    val childId  = res.pathAsString("data.createParent.childReq.id")
    val parentId = res.pathAsString("data.createParent.id")

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.queryThatMustFail(
      s"""
         |mutation {
         |  upsertParent(
         |  where: {id: "$parentId"}
         |  update:{
         |    p: "p2"
         |    childReq: {delete: true}
         |  }
         |  create:{p: "Should not matter" childReq: {create: {c: "Should not matter"}}}
         |  ){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """,
      project,
      errorCode = 0,
      errorContains = "Argument 'update' expected type 'ParentUpdateInput!'"
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
  }

  "a P1! to C1 relation" should "always fail when trying to delete the child" in {
    val project = SchemaDsl.fromString() { schemaP1reqToC1opt }
    database.setup(project)

    val res = server
      .query(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childReq: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |  id
          |    childReq{
          |       id
          |    }
          |  }
          |}""",
        project
      )

    val childId  = res.pathAsString("data.createParent.childReq.id")
    val parentId = res.pathAsString("data.createParent.id")

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.queryThatMustFail(
      s"""
         |mutation {
         |  upsertParent(
         |  where: {id: "$parentId"}
         |  update:{
         |    p: "p2"
         |    childReq: {delete: true}
         |  }
         |  create:{p: "Should not matter" childReq: {create: {c: "Should not matter"}}}
         |  ){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """,
      project,
      errorCode = 0,
      errorContains = "Argument 'update' expected type 'ParentUpdateInput!'"
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
  }

  "a P1 to C1  relation " should "work through a nested mutation by id" in {
    val project = SchemaDsl.fromString() { schemaP1optToC1opt }
    database.setup(project)

    val res = server
      .query(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childOpt: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    id
          |    childOpt{
          |       id
          |    }
          |  }
          |}""",
        project
      )

    val childId  = res.pathAsString("data.createParent.childOpt.id")
    val parentId = res.pathAsString("data.createParent.id")

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    val res2 = server.query(
      s"""
         |mutation {
         |  upsertParent(
         |  where:{id: "$parentId"}
         |  update:{
         |    p: "p2"
         |    childOpt: {delete: true}
         |  }
         |  create:{p: "Should not matter"}
         |  ){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """,
      project
    )

    res2.toString should be("""{"data":{"upsertParent":{"childOpt":null}}}""")

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
  }

  "a P1 to C1  relation" should "error if the nodes are not connected" in {
    val project = SchemaDsl.fromString() { schemaP1optToC1opt }
    database.setup(project)

    val child1Id = server
      .query(
        """mutation {
          |  createChild(data: {c: "c1"})
          |  {
          |    id
          |  }
          |}""",
        project
      )
      .pathAsString("data.createChild.id")

    val parent1Id = server
      .query(
        """mutation {
          |  createParent(data: {p: "p1"})
          |  {
          |    id
          |  }
          |}""",
        project
      )
      .pathAsString("data.createParent.id")

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

    val res = server.queryThatMustFail(
      s"""
         |mutation {
         |  upsertParent(
         |  where:{id: "$parent1Id"}
         |  update:{
         |    p: "p2"
         |    childOpt: {delete: true}
         |  }
         |  create:{p: "Should not matter"}
         |
         |  ){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """,
      project,
      errorCode = 3041
    )

    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
  }

  "a PM to C1!  relation " should "work" in {
    val project = SchemaDsl.fromString() { schemaPMToC1req }
    database.setup(project)

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childrenOpt: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""",
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.query(
      s"""
         |mutation {
         |  upsertParent(
         |    where: {p: "p1"}
         |    update:{
         |    childrenOpt: {delete: {c: "c1"}}
         |  }
         |  create:{p: "Should not matter"}
         |  ){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
  }

  "a P1 to C1!  relation " should "work" in {
    val project = SchemaDsl.fromString() { schemaP1optToC1req }
    database.setup(project)

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childOpt: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childOpt{
        |       c
        |    }
        |  }
        |}""",
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.query(
      s"""
         |mutation {
         |  upsertParent(
         |  where: {p: "p1"}
         |  update:{
         |    childOpt: {delete: true}
         |  }
         |  create:{p: "Should not matter"}
         |  ){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
  }

  "a PM to C1 " should "work" in {
    val project = SchemaDsl.fromString() { schemaPMToC1opt }
    database.setup(project)

    server
      .query(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childrenOpt: {
          |      create: [{c: "c1"}, {c: "c2"}]
          |    }
          |  }){
          |    childrenOpt{
          |       c
          |    }
          |  }
          |}""",
        project
      )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(2) }

    val res = server.query(
      s"""
         |mutation {
         |  upsertParent(
         |  where: { p: "p1"}
         |  update:{
         |    childrenOpt: {delete: [{c: "c2"}]}
         |  }
         |   create:{p: "Should not matter"}
         |  ){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """,
      project
    )

    res.toString should be("""{"data":{"upsertParent":{"childrenOpt":[{"c":"c1"}]}}}""")

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
  }

  "a P1! to CM  relation" should "error " in {
    val project = SchemaDsl.fromString() { schemaP1reqToCM }
    database.setup(project)

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childReq: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childReq{
        |       c
        |    }
        |  }
        |}""",
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.queryThatMustFail(
      s"""
         |mutation {
         |  upsertParent(
         |  where: {p: "p1"}
         |  update:{
         |    childReq: {delete: true}
         |  }
         |  create:{p: "Should not matter",childReq: {create:{c: "Should not matter"}}}
         |  ){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """,
      project,
      errorCode = 0,
      errorContains = "Argument 'update' expected type 'ParentUpdateInput!'"
    )
  }

  "a P1 to CM  relation " should "work" in {
    val project = SchemaDsl.fromString() { schemaP1optToCM }
    database.setup(project)

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childOpt: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childOpt{
        |       c
        |    }
        |  }
        |}""",
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    val res = server.query(
      s"""
         |mutation {
         |  upsertParent(
         |    where: {p: "p1"}
         |    update:{childOpt: {delete: true}}
         |    create:{p: "Should not matter"}
         |  ){
         |    childOpt{
         |      c
         |    }
         |  }
         |}
      """,
      project
    )

    res.toString should be("""{"data":{"upsertParent":{"childOpt":null}}}""")

    server.query(s"""query{children{c, parentsOpt{p}}}""", project).toString should be("""{"data":{"children":[]}}""")

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
  }

  "a PM to CM  relation" should "work" in {
    val project = SchemaDsl.fromString() { schemaPMToCM }
    database.setup(project)

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childrenOpt: {
        |      create: [{c: "c1"},{c: "c2"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""",
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(2) }

    val res = server.query(
      s"""
         |mutation {
         |  upsertParent(
         |  where: { p: "p1"}
         |  update:{
         |    childrenOpt: {delete: [{c: "c1"}, {c: "c2"}]}
         |  }
         |  create:{p: "Should not matter"}
         |  ){
         |    childrenOpt{
         |      c
         |    }
         |  }
         |}
      """,
      project
    )

    res.toString should be("""{"data":{"upsertParent":{"childrenOpt":[]}}}""")

    server.query(s"""query{children{c, parentsOpt{p}}}""", project).toString should be("""{"data":{"children":[]}}""")

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
  }

  "a PM to CM  relation" should "delete fail if other req relations would be violated" in {

    val schema = """type Parent{
                            id: ID! @unique
                            p: String! @unique
                            childrenOpt: [Child!]!
                        }

                        type Child{
                            id: ID! @unique
                            c: String! @unique
                            parentsOpt: [Parent!]!
                            otherReq: ReqOther!
                        }
                       
                        type ReqOther{
                            id: ID! @unique
                            r: String! @unique
                            childReq: Child!
                        }"""

    val project = SchemaDsl.fromString() { schema }
    database.setup(project)

    server.query(
      """mutation {
        |  createReqOther(data: {
        |    r: "r1"
        |    childReq: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    r
        |  }
        |}""",
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToReqOther").await should be(1) }

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childrenOpt: {
        |      connect: {c: "c1"}
        |    }
        |  }){
        |    p
        |  }
        |}""",
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.queryThatMustFail(
      s"""
         |mutation {
         |  upsertParent(
         |  where: { p: "p1"}
         |  update:{
         |    childrenOpt: {delete: [{c: "c1"}]}
         |  }
         |  create:{p: "Should not matter"}
         |
         |  ){
         |    childrenOpt{
         |      c
         |    }
         |  }
         |}
      """,
      project,
      errorCode = 3042,
      errorContains = """The change you are trying to make would violate the required relation 'ChildToReqOther' between Child and ReqOther"""
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToReqOther").await should be(1) }

  }

  "a PM to CM  relation" should "delete the child from other relations as well" in {
    val schema = """type Parent{
                            id: ID! @unique
                            p: String! @unique
                            childrenOpt: [Child!]!
                        }

                        type Child{
                            id: ID! @unique
                            c: String! @unique
                            parentsOpt: [Parent!]!
                            otherOpt: OptOther
                        }

                        type OptOther{
                            id: ID! @unique
                            o: String! @unique
                            childOpt: Child
                        }"""

    val project = SchemaDsl.fromString() { schema }
    database.setup(project)

    server.query(
      """mutation {
        |  createOptOther(data: {
        |    o: "o1"
        |    childOpt: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    o
        |  }
        |}""",
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToOptOther").await should be(1) }

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childrenOpt: {
        |      connect: {c: "c1"}
        |    }
        |  }){
        |    p
        |  }
        |}""",
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    val res = server.query(
      s"""
         |mutation {
         |  upsertParent(
         |  where: { p: "p1"}
         |  update:{
         |    childrenOpt: {delete: [{c: "c1"}]}
         |  }
         |  create:{p:"Should not matter"}
         |  ){
         |    childrenOpt{
         |      c
         |    }
         |  }
         |}
      """,
      project
    )

    res.toString should be("""{"data":{"upsertParent":{"childrenOpt":[]}}}""")

    server.query(s"""query{children{c, parentsOpt{p}}}""", project).toString should be("""{"data":{"children":[]}}""")

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToOptOther").await should be(0) }
  }

  "a one to many relation" should "be deletable by id through a nested mutation" in {
    val schema = """type Comment{
                            id: ID! @unique
                            text: String
                            todo: Todo
                        }

                        type Todo{
                            id: ID! @unique
                            text: String
                            comments: [Comment!]!
                        }"""

    val project = SchemaDsl.fromString() { schema }
    database.setup(project)

    val createResult = server.query(
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
        |}""",
      project
    )

    val todoId     = createResult.pathAsString("data.createTodo.id")
    val comment1Id = createResult.pathAsString("data.createTodo.comments.[0].id")
    val comment2Id = createResult.pathAsString("data.createTodo.comments.[1].id")

    val result = server.query(
      s"""mutation {
         |  upsertTodo(
         |    where: {
         |      id: "$todoId"
         |    }
         |    update:{
         |      comments: {
         |        delete: [{id: "$comment1Id"}, {id: "$comment2Id"}]
         |      }
         |    }
         |    create:{text: "Should not matter"}
         |  ){
         |    comments {
         |      text
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.upsertTodo.comments").toString, """[]""")

    val query = server.query("""{ comments { id }}""", project)
    mustBeEqual(query.toString, """{"data":{"comments":[]}}""")
    ifConnectorIsActive { dataResolver(project).countByTable("_RelayId").await should be(1) }
  }

  "a one to many relation" should "be deletable by any unique argument through a nested mutation" in {

    val schema = """type Comment{
                            id: ID! @unique
                            text: String
                            alias: String! @unique
                            todo: Todo
                        }

                        type Todo{
                            id: ID! @unique
                            text: String
                            comments: [Comment!]!
                        }"""

    val project = SchemaDsl.fromString() { schema }
    database.setup(project)

    val createResult = server.query(
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
        |}""",
      project
    )
    val todoId = createResult.pathAsString("data.createTodo.id")

    val result = server.query(
      s"""mutation {
         |  upsertTodo(
         |    where: {
         |      id: "$todoId"
         |    }
         |    update:{
         |      comments: {
         |        delete: [{alias: "alias1"}, {alias: "alias2"}]
         |      }
         |    }
         |    create:{text:"Should not matter"}
         |  ){
         |    comments {
         |      text
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.upsertTodo.comments").toString, """[]""")

    val query = server.query("""{ comments { id }}""", project)
    mustBeEqual(query.toString, """{"data":{"comments":[]}}""")
    ifConnectorIsActive { dataResolver(project).countByTable("_RelayId").await should be(1) }
  }

  "a many to one relation" should "be deletable by id through a nested mutation" in {
    val schema = """type Comment{
                            id: ID! @unique
                            text: String
                            todo: Todo
                        }

                        type Todo{
                            id: ID! @unique
                            text: String
                            comments: [Comment!]!
                        }"""

    val project = SchemaDsl.fromString() { schema }
    database.setup(project)

    val createResult = server.query(
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
        |}""",
      project
    )
    val todoId    = createResult.pathAsString("data.createTodo.id")
    val commentId = createResult.pathAsString("data.createTodo.comments.[0].id")

    val result = server.query(
      s"""
         |mutation {
         |  upsertComment(
         |    where: {
         |      id: "$commentId"
         |    }
         |    update: {
         |      todo: {
         |        delete: true
         |      }
         |    }
         |    create:{text:"Should not matter"}
         |
         |  ){
         |    todo {
         |      id
         |    }
         |  }
         |}
      """,
      project
    )
    mustBeEqual(result.pathAsJsValue("data.upsertComment").toString, """{"todo":null}""")

    val query = server.query("""{ todoes { id }}""", project)
    mustBeEqual(query.toString, """{"data":{"todoes":[]}}""")
  }

  "one2one relation both exist and are connected" should "be deletable by id through a nested mutation" in {
    val schema = """type Note{
                            id: ID! @unique
                            text: String
                            todo: Todo
                        }

                        type Todo{
                            id: ID! @unique
                            title: String!
                            note: Note
                        }"""

    val project = SchemaDsl.fromString() { schema }
    database.setup(project)

    val createResult = server.query(
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
        |}""",
      project
    )
    val noteId = createResult.pathAsString("data.createNote.id")
    val todoId = createResult.pathAsString("data.createNote.todo.id")

    val result = server.query(
      s"""
         |mutation {
         |  upsertNote(
         |    where: {
         |      id: "$noteId"
         |    }
         |    update: {
         |      todo: {
         |        delete: true
         |      }
         |    }
         |    create:{text:"Should not matter"}
         |
         |  ){
         |    todo {
         |      title
         |    }
         |  }
         |}
      """,
      project
    )
    mustBeEqual(result.pathAsJsValue("data.upsertNote").toString, """{"todo":null}""")

    val query = server.query("""{ todoes { id }}""", project)
    mustBeEqual(query.toString, """{"data":{"todoes":[]}}""")
  }

  "one2one relation both exist and are connected" should "be deletable by unique field through a nested mutation" in {
    val schema = """type Note{
                            id: ID! @unique
                            text: String @unique
                            todo: Todo
                        }

                        type Todo{
                            id: ID! @unique
                            title: String! @unique
                            note: Note
                        }"""

    val project = SchemaDsl.fromString() { schema }
    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createNote(
        |    data: {
        |      text: "FirstUnique"
        |      todo: {
        |        create: { title: "the title" }
        |      }
        |    }
        |  ){
        |    id
        |  }
        |}""",
      project
    )

    val result = server.query(
      s"""
         |mutation {
         |  upsertNote(
         |    where: {
         |      text: "FirstUnique"
         |    }
         |    update: {
         |      todo: {
         |        delete: true
         |      }
         |    }
         |    create:{text:"Should not matter"}
         |  ){
         |    todo {
         |      title
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.upsertNote").toString, """{"todo":null}""")

    val query = server.query("""{ todoes { id }}""", project)
    mustBeEqual(query.toString, """{"data":{"todoes":[]}}""")

    val query2 = server.query("""{ notes { text }}""", project)
    mustBeEqual(query2.toString, """{"data":{"notes":[{"text":"FirstUnique"}]}}""")
  }

  "a one to one relation" should "not do a nested delete by id if the nested node does not exist" in {
    val schema = """type Note{
                            id: ID! @unique
                            text: String
                            todo: Todo
                        }

                        type Todo{
                            id: ID! @unique
                            title: String!
                            note: Note
                        }"""

    val project = SchemaDsl.fromString() { schema }
    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createNote(
        |    data: {
        |      text: "Note"
        |    }
        |  ){
        |    id
        |    todo { id }
        |  }
        |}""",
      project
    )
    val noteId = createResult.pathAsString("data.createNote.id")

    val result = server.queryThatMustFail(
      s"""
         |mutation {
         |  upsertNote(
         |    where: {id: "$noteId"}
         |    update: {
         |      todo: {
         |        delete: true
         |      }
         |    }
         |    create:{text:"Should not matter"}
         |  ){
         |    todo {
         |      title
         |    }
         |  }
         |}
      """,
      project,
      errorCode = 3041,
      errorContains =
        s"The relation NoteToTodo has no node for the model Note with the value '$noteId' for the field 'id' connected to a node for the model Todo on your mutation path."
    )

    val query = server.query("""{ todoes { title }}""", project)
    mustBeEqual(query.toString, """{"data":{"todoes":[]}}""")

    val query2 = server.query("""{ notes { text }}""", project)
    mustBeEqual(query2.toString, """{"data":{"notes":[{"text":"Note"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only node edges on the path" in {
    val project = SchemaDsl.fromString() { """type Top {
                                             |  id: ID! @unique
                                             |  nameTop: String! @unique
                                             |  middles: [Middle!]!
                                             |}
                                             |
                                             |type Middle {
                                             |  id: ID! @unique
                                             |  nameMiddle: String! @unique
                                             |  tops: [Top!]!
                                             |  bottoms: [Bottom!]!
                                             |}
                                             |
                                             |type Bottom {
                                             |  id: ID! @unique
                                             |  nameBottom: String! @unique
                                             |  middles: [Middle!]!
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[ 
        |        {
        |          nameMiddle: "the middle"
        |          bottoms: {
        |            create: [{ nameBottom: "the bottom"}, { nameBottom: "the second bottom"}]
        |          }
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottoms: {
        |            create: [{nameBottom: "the third bottom"},{nameBottom: "the fourth bottom"}]
        |          }
        |        }
        |     ]
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        upsert: [{
         |              where: {nameMiddle: "the middle"},
         |              update:{  nameMiddle: "updated middle"
         |                      bottoms: {delete: [{nameBottom: "the bottom"}]}}
         |              create:{nameMiddle:"Should not matter"}
         |         }]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles {
         |      nameMiddle
         |      bottoms {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottoms":[{"nameBottom":"the second bottom"}]},{"nameMiddle":"the second middle","bottoms":[{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}]}}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only node edges on the path and there are no backrelations" in {
    val project = SchemaDsl.fromString() { """type Top {
                                             |  id: ID! @unique
                                             |  nameTop: String! @unique
                                             |  middles: [Middle!]!
                                             |}
                                             |
                                             |type Middle {
                                             |  id: ID! @unique
                                             |  nameMiddle: String! @unique
                                             |  bottoms: [Bottom!]!
                                             |}
                                             |
                                             |type Bottom {
                                             |  id: ID! @unique
                                             |  nameBottom: String! @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottoms: {
        |            create: [{ nameBottom: "the bottom"}, { nameBottom: "the second bottom"}]
        |          }
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottoms: {
        |            create: [{nameBottom: "the third bottom"},{nameBottom: "the fourth bottom"}]
        |          }
        |        }
        |     ]
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        upsert: [{
         |              where:{nameMiddle: "the middle"},
         |              update:{nameMiddle: "updated middle"
         |                      bottoms: {delete: [{nameBottom: "the bottom"}]}}
         |              create:{nameMiddle:"Should not matter"}
         |              }]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles {
         |      nameMiddle
         |      bottoms {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottoms":[{"nameBottom":"the second bottom"}]},{"nameMiddle":"the second middle","bottoms":[{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}]}}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are model and node edges on the path " in {
    val project = SchemaDsl.fromString() { """type Top {
                                             |  id: ID! @unique
                                             |  nameTop: String! @unique
                                             |  middles: [Middle!]!
                                             |}
                                             |
                                             |type Middle {
                                             |  id: ID! @unique
                                             |  nameMiddle: String! @unique
                                             |  tops: [Top!]!
                                             |  bottom: Bottom
                                             |}
                                             |
                                             |type Bottom {
                                             |  id: ID! @unique
                                             |  nameBottom: String! @unique
                                             |  middle: Middle
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {create: { nameBottom: "the bottom"}}
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottom: {create: { nameBottom: "the second bottom"}}
        |        }
        |     ]
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        upsert: [{
         |              where: {nameMiddle: "the middle"},
         |              update:{nameMiddle: "updated middle"
         |                      bottom: {delete: true}}
         |              create:{nameMiddle:"Should not matter"}
         |              }]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottom":null},{"nameMiddle":"the second middle","bottom":{"nameBottom":"the second bottom"}}]}}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are model and node edges on the path  and back relations are missing and node edges follow model edges" in {
    val project = SchemaDsl.fromString() { """type Top {
                                             |  id: ID! @unique
                                             |  nameTop: String! @unique
                                             |  middle: Middle
                                             |}
                                             |
                                             |type Middle {
                                             |  id: ID! @unique
                                             |  nameMiddle: String! @unique
                                             |  bottom: Bottom
                                             |}
                                             |
                                             |type Bottom {
                                             |  id: ID! @unique
                                             |  nameBottom: String! @unique
                                             |  below: [Below!]!
                                             |}
                                             |
                                             |type Below {
                                             |  id: ID! @unique
                                             |  nameBelow: String! @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation a {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {
        |            create: { nameBottom: "the bottom"
        |            below: {
        |            create: [{ nameBelow: "below"}, { nameBelow: "second below"}]}
        |        }}}
        |        }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |               nameMiddle: "updated middle"
         |               bottom: {
         |                upsert: {
         |                  update:{
         |                    nameBottom: "updated bottom"
         |                    below: { delete: {nameBelow: "below"}}}
         |                create:{nameBottom:"Should not matter"}
         |          }
         |         }
         |       }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |        below{
         |           nameBelow
         |        }
         |        
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":{"nameBottom":"updated bottom","below":[{"nameBelow":"second below"}]}}}}}""")
  }
}
