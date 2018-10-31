package com.prisma.api.mutations.nonEmbedded.nestedMutations

import com.prisma.api.ApiSpecBase
import com.prisma.shared.models.ApiConnectorCapability.JoinRelationsCapability
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class NestedUpdateManyMutationInsideUpdateSpec extends FlatSpec with Matchers with ApiSpecBase with SchemaBase {
  override def runOnlyForCapabilities = Set(JoinRelationsCapability)

  "A 1-n relation" should "error if trying to use nestedUpdateMany" in {
    val project = SchemaDsl.fromString() { schemaP1optToC1opt }
    database.setup(project)

    val parent1Id = server
      .query(
        """mutation {
          |  createParent(data: {p: "p1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.id")

    val res = server.queryThatMustFail(
      s"""
         |mutation {
         |  updateParent(
         |  where:{id: "$parent1Id"}
         |  data:{
         |    p: "p2"
         |    childOpt: {updateMany: {
         |        where:{c: "c"}
         |        data: {c: "newC"}
         |    
         |    }}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 0,
      errorContains = """ Reason: 'childOpt.updateMany' Field 'updateMany' is not defined in the input type 'ChildUpdateOneWithoutParentOptInput'."""
    )
  }

  "a PM to C1!  relation " should "work" in {
    val project = SchemaDsl.fromString() { schemaPMToC1req }
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
        |}""".stripMargin,
      project
    )

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p2"
        |    childrenOpt: {
        |      create: [{c: "c3"},{c: "c4"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }

    server.query(
      s"""
         |mutation {
         |  updateParent(
         |    where: {p: "p1"}
         |    data:{
         |    childrenOpt: {updateMany: {
         |        where: {c_contains:"c"}
         |        data: {test: "updated"}
         |    }}
         |  }){
         |    childrenOpt {
         |      c
         |      test
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(2)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(4)

    server.query("query{parents{p,childrenOpt{c, test}}}", project).toString() should be(
      """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","test":"updated"},{"c":"c2","test":"updated"}]},{"p":"p2","childrenOpt":[{"c":"c3","test":null},{"c":"c4","test":null}]}]}}""")
  }

  "a PM to C1  relation " should "work" in {
    val project = SchemaDsl.fromString() { schemaPMToC1opt }
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
        |}""".stripMargin,
      project
    )

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p2"
        |    childrenOpt: {
        |      create: [{c: "c3"},{c: "c4"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }

    server.query(
      s"""
         |mutation {
         |  updateParent(
         |    where: {p: "p1"}
         |    data:{
         |    childrenOpt: {updateMany: {
         |        where: {c_contains:"c"}
         |        data: {test: "updated"}
         |    }}
         |  }){
         |    childrenOpt {
         |      c
         |      test
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(2)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(4)

    server.query("query{parents{p,childrenOpt{c, test}}}", project).toString() should be(
      """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","test":"updated"},{"c":"c2","test":"updated"}]},{"p":"p2","childrenOpt":[{"c":"c3","test":null},{"c":"c4","test":null}]}]}}""")
  }

  "a PM to CM  relation " should "work" in {
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
        |}""".stripMargin,
      project
    )

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p2"
        |    childrenOpt: {
        |      create: [{c: "c3"},{c: "c4"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }

    server.query(
      s"""
         |mutation {
         |  updateParent(
         |    where: {p: "p1"}
         |    data:{
         |    childrenOpt: {updateMany: {
         |        where: {c_contains:"c"}
         |        data: {test: "updated"}
         |    }}
         |  }){
         |    childrenOpt {
         |      c
         |      test
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(2)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(4)

    server.query("query{parents{p,childrenOpt{c, test}}}", project).toString() should be(
      """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","test":"updated"},{"c":"c2","test":"updated"}]},{"p":"p2","childrenOpt":[{"c":"c3","test":null},{"c":"c4","test":null}]}]}}""")
  }

  "a PM to C1!  relation " should "work with several updateManys" in {
    val project = SchemaDsl.fromString() { schemaPMToC1req }
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
        |}""".stripMargin,
      project
    )

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p2"
        |    childrenOpt: {
        |      create: [{c: "c3"},{c: "c4"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }

    server.query(
      s"""
         |mutation {
         |  updateParent(
         |    where: {p: "p1"}
         |    data:{
         |    childrenOpt: {updateMany: [
         |    {
         |        where: {c_contains:"1"}
         |        data: {test: "updated1"}
         |    },
         |    {
         |        where: {c_contains:"2"}
         |        data: {test: "updated2"}
         |    }
         |    ]}
         |  }){
         |    childrenOpt {
         |      c
         |      test
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(2)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(4)

    server.query("query{parents{p,childrenOpt{c, test}}}", project).toString() should be(
      """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","test":"updated1"},{"c":"c2","test":"updated2"}]},{"p":"p2","childrenOpt":[{"c":"c3","test":null},{"c":"c4","test":null}]}]}}""")
  }

  "a PM to C1!  relation " should "work with empty Filter" in {
    val project = SchemaDsl.fromString() { schemaPMToC1req }
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
        |}""".stripMargin,
      project
    )

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p2"
        |    childrenOpt: {
        |      create: [{c: "c3"},{c: "c4"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }

    server.query(
      s"""
         |mutation {
         |  updateParent(
         |    where: {p: "p1"}
         |    data:{
         |    childrenOpt: {updateMany: [
         |    {
         |        where: {}
         |        data: {test: "updated1"}
         |    }
         |    ]}
         |  }){
         |    childrenOpt {
         |      c
         |      test
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(2)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(4)

    server.query("query{parents{p,childrenOpt{c, test}}}", project).toString() should be(
      """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","test":"updated1"},{"c":"c2","test":"updated1"}]},{"p":"p2","childrenOpt":[{"c":"c3","test":null},{"c":"c4","test":null}]}]}}""")
  }

  "a PM to C1!  relation " should "not change anything when there is no hit" in {
    val project = SchemaDsl.fromString() { schemaPMToC1req }
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
        |}""".stripMargin,
      project
    )

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p2"
        |    childrenOpt: {
        |      create: [{c: "c3"},{c: "c4"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }

    server.query(
      s"""
         |mutation {
         |  updateParent(
         |    where: {p: "p1"}
         |    data:{
         |    childrenOpt: {updateMany: [
         |    {
         |        where: {c_contains:"3"}
         |        data: {test: "updated3"}
         |    },
         |    {
         |        where: {c_contains:"4"}
         |        data: {test: "updated4"}
         |    }
         |    ]}
         |  }){
         |    childrenOpt {
         |      c
         |      test
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(2)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(4)

    server.query("query{parents{p,childrenOpt{c, test}}}", project).toString() should be(
      """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","test":null},{"c":"c2","test":null}]},{"p":"p2","childrenOpt":[{"c":"c3","test":null},{"c":"c4","test":null}]}]}}""")
  }

  //optional ordering

  "a PM to C1!  relation " should "work when multiple filters hit" in {
    val project = SchemaDsl.fromString() { schemaPMToC1req }
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
        |}""".stripMargin,
      project
    )

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p2"
        |    childrenOpt: {
        |      create: [{c: "c3"},{c: "c4"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }

    server.query(
      s"""
         |mutation {
         |  updateParent(
         |    where: {p: "p1"}
         |    data:{
         |    childrenOpt: {updateMany: [
         |    {
         |        where: {c_contains:"c"}
         |        data: {test: "updated1"}
         |    },
         |    {
         |        where: {c_contains:"c1"}
         |        data: {test: "updated2"}
         |    }
         |    ]}
         |  }){
         |    childrenOpt {
         |      c
         |      test
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(2)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(4)

    server.query("query{parents{p,childrenOpt{c, test}}}", project).toString() should be(
      """{"data":{"parents":[{"p":"p1","childrenOpt":[{"c":"c1","test":"updated2"},{"c":"c2","test":"updated1"}]},{"p":"p2","childrenOpt":[{"c":"c3","test":null},{"c":"c4","test":null}]}]}}""")
  }

}
