package com.prisma.api.mutations

import com.prisma.api.ApiSpecBase
import com.prisma.api.mutations.nonEmbedded.nestedMutations.SchemaBase
import com.prisma.shared.models.ApiConnectorCapability.JoinRelationsCapability
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class DeleteManyMutationRelationsSpec extends FlatSpec with Matchers with ApiSpecBase with SchemaBase {
  override def runOnlyForCapabilities = Set(JoinRelationsCapability)

  "a P0 to C1! relation " should "error when deleting the parent" in {

    val schema = """type Parent{
                            id: ID! @unique
                            p: String! @unique
                        }
                        
                        type Child{
                            id: ID! @unique
                            c: String! @unique
                            parentReq: Parent!
                        }"""

    val project = SchemaDsl.fromString() { schema }
    database.setup(project)

    server
      .query(
        """mutation {
          |  createChild(data: {
          |    c: "c1"
          |    parentReq: {
          |      create: {p: "p1"}
          |    }
          |  }){
          |    id
          |  }
          |}""".stripMargin,
        project
      )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.queryThatMustFail(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {p: "p1"}
         |  ){
         |  count
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3042,
      errorContains = "The change you are trying to make would violate the required relation 'ChildToParent' between Child and Parent"
    )

    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
  }

  "a P0 to C1! relation " should "error when deleting the parent with empty filter" in {
    val schema = """type Parent{
                            id: ID! @unique
                            p: String! @unique
                        }
                        
                        type Child{
                            id: ID! @unique
                            c: String! @unique
                            parentReq: Parent!
                        }"""

    val project = SchemaDsl.fromString() { schema }
    database.setup(project)

    server
      .query(
        """mutation {
          |  createChild(data: {
          |    c: "c1"
          |    parentReq: {
          |      create: {p: "p1"}
          |    }
          |  }){
          |    id
          |  }
          |}""".stripMargin,
        project
      )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.queryThatMustFail(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {}
         |  ){
         |  count
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3042,
      errorContains = "The change you are trying to make would violate the required relation 'ChildToParent' between Child and Parent"
    )

    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
  }

  "a P1! to C1! relation " should "error when deleting the parent" in {
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
          |}""".stripMargin,
        project
      )
    val childId  = res.pathAsString("data.createParent.childReq.id")
    val parentId = res.pathAsString("data.createParent.id")

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.queryThatMustFail(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {id: "$parentId"}
         |  ){
         |  count
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3042,
      errorContains = "The change you are trying to make would violate the required relation 'ChildToParent' between Child and Parent"
    )

    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
  }

  "a P1! to C1 relation" should "succeed when trying to delete the parent" in {
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
          |}""".stripMargin,
        project
      )

    val parentId = res.pathAsString("data.createParent.id")

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {id: "$parentId"}
         |  ){
         |  count
         |  }
         |}
      """.stripMargin,
      project
    )

    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
  }

  "a P1 to C1  relation " should "succeed when trying to delete the parent" in {
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
          |}""".stripMargin,
        project
      )

    val parentId = res.pathAsString("data.createParent.id")

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {id: "$parentId"}
         |  ){
         |  count
         |  }
         |}
      """.stripMargin,
      project
    )

    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
  }

  "a P1 to C1  relation " should "succeed when trying to delete the parent if there are no children" in {
    val project = SchemaDsl.fromString() { schemaP1optToC1opt }

    database.setup(project)

    server
      .query(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |  }){
          |    id
          |  }
          |}""".stripMargin,
        project
      )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {p: "p1"}
         |  ){
         |  count
         |  }
         |}
      """.stripMargin,
      project
    )

    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
  }

  "a PM to C1!  relation " should "error when deleting the parent" in {
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
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.queryThatMustFail(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {p: "p1"}
         |  ){
         |  count
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3042,
      errorContains = "The change you are trying to make would violate the required relation 'ChildToParent' between Child and Parent"
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
  }

  "a PM to C1!  relation " should "succeed if no child exists that requires the parent" in {
    val project = SchemaDsl.fromString() { schemaPMToC1req }

    database.setup(project)

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {p: "p1"}
         |  ){
         |  count
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    dataResolver(project).countByTable("Parent").await should be(0)
    dataResolver(project).countByTable("Child").await should be(0)

  }

  "a P1 to C1!  relation " should "error when trying to delete the parent" in {
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
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.queryThatMustFail(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {p: "p1"}
         |  ){
         |  count
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3042,
      errorContains = "The change you are trying to make would violate the required relation 'ChildToParent' between Child and Parent"
    )
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
  }

  "a P1 to C1!  relation " should "succeed when trying to delete the parent if there is no child" in {
    val project = SchemaDsl.fromString() { schemaP1optToC1req }

    database.setup(project)

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |
        |  }){
        |    p
        |  }
        |}""".stripMargin,
      project
    )

    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {p: "p1"}
         |  ){
         |  count
         |  }
         |}
      """.stripMargin,
      project
    )
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
  }

  "a PM to C1 " should "succeed in deleting the parent" in {
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
          |}""".stripMargin,
        project
      )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(2) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: { p: "p1"}
         |  ){
         |    count
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(2)
  }

  "a PM to C1 " should "succeed in deleting the parent if there is no child" in {
    val project = SchemaDsl.fromString() { schemaPMToC1opt }

    database.setup(project)

    server
      .query(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |  }){
          |    p
          |  }
          |}""".stripMargin,
        project
      )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: { p: "p1"}
         |  ){
         |    count
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
  }

  "a P1! to CM  relation" should "should succeed in deleting the parent " in {
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
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {p: "p1"}
         |  ){
         |    count
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
  }

  "a P1 to CM  relation " should " should succeed in deleting the parent" in {
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
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {p: "p1"}
         |  ){
         |    count
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
  }

  "a P1 to CM  relation " should " should succeed in deleting the parent if there is no child" in {
    val project = SchemaDsl.fromString() { schemaP1optToCM }

    database.setup(project)

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |
        |  }){
        |    p
        |  }
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {p: "p1"}
         |  ){
         |    count
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    dataResolver(project).countByTable("Parent").await should be(0)
    dataResolver(project).countByTable("Child").await should be(0)
  }

  "a PM to CM  relation" should "succeed in deleting the parent" in {
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

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(2) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {p: "p1"}
         |  ){
         |    count
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(2)

  }

  "a PM to CM  relation" should "succeed in deleting the parent if there is no child" in {
    val project = SchemaDsl.fromString() { schemaPMToCM }

    database.setup(project)

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |
        |  }){
        |    p
        |  }
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: {p: "p1"}
         |  ){
         |    count
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)

  }

  "a PM to CM  relation" should "delete the parent from other relations as well" in {

    val schema = """    type Parent{
                            id: ID! @unique
                            p: String! @unique
                            childrenOpt: [Child!]!
                            stepChildOpt: StepChild
                        }

                        type Child{
                            id: ID! @unique
                            c: String! @unique
                            parentsOpt: [Parent!]!
                        }
                 
                        type StepChild{
                             id: ID! @unique
                             s: String! @unique
                             parentOpt: Parent
                         }"""

    val project = SchemaDsl.fromString() { schema }
    database.setup(project)

    server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childrenOpt: {
        |      create: [{c: "c1"},{c: "c2"}]
        |    }
        |    stepChildOpt: {
        |      create: {s: "s1"}
        |    }
        |  }){
        |    p
        |  }
        |}""".stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_StepChildToParent").await should be(1) }
    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(2) }

    server.query(
      s"""
         |mutation {
         |  deleteManyParents(
         |  where: { p: "p1"}
         | ){
         |  count
         |  }
         |}
      """.stripMargin,
      project
    )

    ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    ifConnectorIsActive { dataResolver(project).countByTable("_StepChildToParent").await should be(0) }
    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
    dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(2)
    dataResolver(project).countByTable(project.schema.getModelByName_!("StepChild").dbName).await should be(1)
  }
}
