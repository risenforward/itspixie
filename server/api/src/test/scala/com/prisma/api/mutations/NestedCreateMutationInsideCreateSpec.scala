package com.prisma.api.mutations

import com.prisma.api.ApiBaseSpec
import com.prisma.api.database.DatabaseQueryBuilder
import com.prisma.shared.project_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class NestedCreateMutationInsideCreateSpec extends FlatSpec with Matchers with ApiBaseSpec {

  "a P1! to C1! relation" should "be possible" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToOneRelation_!("parentReq", "childReq", parent)
    }
    database.setup(project)

    val res = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childReq: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    p
          |    childReq{
          |       c
          |    }
          |  }
          |}""".stripMargin,
        project
      )

    res.toString should be("""{"data":{"createParent":{"p":"p1","childReq":{"c":"c1"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))

  }

  "a P1! to C1 relation " should "work" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation_!("childReq", "parentOpt", child, isRequiredOnOtherField = false)
    }
    database.setup(project)

    val child1Id = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childReq: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    childReq{
          |       id
          |    }
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.childReq.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))

  }

  "a P1! to C1  relation with the child not in a relation" should "be connectable through a nested mutation by id" ignore {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation_!("childReq", "parentOpt", child, isRequiredOnOtherField = false)
    }
    database.setup(project)

    val child1Id = server
      .executeQuerySimple(
        """mutation {
          |  createChild(data: {c: "c1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createChild.id")

    val parentId = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p2"
          |    childReq: {
          |      create: {c: "c2"}
          |    }
          |  }){
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: {id: "$parentId"}
         |  data:{
         |    p: "p2"
         |    childReq: {connect: {id: "$child1Id"}}
         |  }){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childReq":{"c":"c1"}}}}""")
    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a P1 to C1  relation with the child already in a relation" should "be connectable through a nested mutation by id if the child is already in a relation" ignore {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation("childOpt", "parentOpt", child)
    }
    database.setup(project)

    val child1Id = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childOpt: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    childOpt{
          |       id
          |    }
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.childOpt.id")

    val parentId2 = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p2"
          |    childOpt: {
          |      create: {c: "c2"}
          |    }
          |  }){
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(2))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where:{id: "$parentId2"}
         |  data:{
         |    p: "p2"
         |    childOpt: {connect: {id: "$child1Id"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childOpt":{"c":"c1"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a P1 to C1  relation with the child and the parent without a relation" should "be connectable through a nested mutation by id" ignore {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation("childOpt", "parentOpt", child)
    }
    database.setup(project)

    val child1Id = server
      .executeQuerySimple(
        """mutation {
          |  createChild(data: {c: "c1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createChild.id")

    val parent1Id = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {p: "p1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where:{id: "$parent1Id"}
         |  data:{
         |    p: "p2"
         |    childOpt: {connect: {id: "$child1Id"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childOpt":{"c":"c1"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a P1 to C1  relation with the child without a relation" should "be connectable through a nested mutation by id" ignore {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation("childOpt", "parentOpt", child)
    }
    database.setup(project)

    val child1Id = server
      .executeQuerySimple(
        """mutation {
          |  createChild(data: {c: "c1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createChild.id")

    val parentId = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childOpt: {
          |      create: {c: "c2"}
          |    }
          |  }){
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where:{id: "$parentId"}
         |  data:{
         |    p: "p2"
         |    childOpt: {connect: {id: "$child1Id"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childOpt":{"c":"c1"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a P1 to C1  relation with the parent without a relation" should "be connectable through a nested mutation by id" ignore {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation("childOpt", "parentOpt", child)
    }
    database.setup(project)

    val parentId = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {p: "p1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.id")

    val childId = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p2"
          |    childOpt: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    childOpt{id}
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.childOpt.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where:{id: "$parentId"}
         |  data:{
         |    childOpt: {connect: {id: "$childId"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childOpt":{"c":"c1"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a PM to C1!  relation with the child already in a relation" should "be connectable through a nested mutation by unique" ignore {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToManyRelation_!("childrenOpt", "parentReq", child)
    }
    database.setup(project)

    server.executeQuerySimple(
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

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p2"
        |    childrenOpt: {
        |      create: {c: "c2"}
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(2))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |    where: {p: "p2"}
         |    data:{
         |    childrenOpt: {connect: {c: "c1"}}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"}]}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(2))
  }

  "a P1 to C1!  relation with the child and the parent already in a relation" should "should error in a nested mutation by unique" ignore {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      schema.model("Child").field_!("c", _.String, isUnique = true).oneToOneRelation_!("parentReq", "childOpt", parent, isRequiredOnOtherField = false)
    }
    database.setup(project)

    server.executeQuerySimple(
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

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p2"
        |    childOpt: {
        |      create: {c: "c2"}
        |    }
        |  }){
        |    childOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(2))

    server.executeQuerySimpleThatMustFail(
      s"""
         |mutation {
         |  updateParent(
         |  where: {p: "p2"} 
         |  data:{
         |    childOpt: {connect: {c: "c1"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3042,
      errorContains = "The change you are trying to make would violate a required relation between Parent and Child"
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(2))
  }

  "a P1 to C1!  relation with the child already in a relation" should "should error in a nested mutation by unique" ignore {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      schema.model("Child").field_!("c", _.String, isUnique = true).oneToOneRelation_!("parentReq", "childOpt", parent, isRequiredOnOtherField = false)
    }
    database.setup(project)

    server.executeQuerySimple(
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

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p2"
        |  }){
        |  p
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: {p: "p2"} 
         |  data:{
         |    childOpt: {connect: {c: "c1"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childOpt":{"c":"c1"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a PM to C1  relation with the child already in a relation" should "be connectable through a nested mutation by unique" ignore {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToManyRelation("childrenOpt", "parentOpt", child)
    }
    database.setup(project)

    server
      .executeQuerySimple(
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

    server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {p: "p2"}){
          |    p
          |  }
          |}""".stripMargin,
        project
      )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(2))

    // we are even resilient against multiple identical connects here -> twice connecting to c2

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: { p: "p2"}
         |  data:{
         |    childrenOpt: {connect: [{c: "c1"},{c: "c2"},{c: "c2"}]}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"}]}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(2))
  }

  "a PM to C1  relation with the child without a relation" should "be connectable through a nested mutation by unique" ignore {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToManyRelation("childrenOpt", "parentOpt", child)
    }
    database.setup(project)

    server
      .executeQuerySimple(
        """mutation {
          |  createChild(data: {c: "c1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )

    server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {p: "p1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: {p:"p1"}
         |  data:{
         |    childrenOpt: {connect: {c: "c1"}}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"}]}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a P1! to CM  relation with the child already in a relation" should "be connectable through a nested mutation by unique" ignore {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToManyRelation_!("parentsOpt", "childReq", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
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

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p2"
        |    childReq: {
        |      create: {c: "c2"}
        |    }
        |  }){
        |    childReq{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(2))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: {p: "p2"}
         |  data:{
         |    childReq: {connect: {c: "c1"}}
         |  }){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childReq":{"c":"c1"}}}}""")

    server.executeQuerySimple(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
      """{"data":{"children":[{"c":"c1","parentsOpt":[{"p":"p1"},{"p":"p2"}]},{"c":"c2","parentsOpt":[]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(2))
  }

  "a P1! to CM  relation with the child not already in a relation" should "be connectable through a nested mutation by unique" ignore {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToManyRelation_!("parentsOpt", "childReq", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createChild(data: {c: "c1"}){
        |       c
        |  }
        |}""".stripMargin,
      project
    )

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p2"
        |    childReq: {
        |      create: {c: "c2"}
        |    }
        |  }){
        |    childReq{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: {p: "p2"}
         |  data:{
         |    childReq: {connect: {c: "c1"}}
         |  }){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childReq":{"c":"c1"}}}}""")

    server.executeQuerySimple(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
      """{"data":{"children":[{"c":"c1","parentsOpt":[{"p":"p2"}]},{"c":"c2","parentsOpt":[]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a P1 to CM  relation with the child already in a relation" should "be connectable through a nested mutation by unique" ignore {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToManyRelation("parentsOpt", "childOpt", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
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

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {p: "p2"}){
        |    p
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |    where: {p: "p2"}
         |    data:{
         |    childOpt: {connect: {c: "c1"}}
         |  }){
         |    childOpt{
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childOpt":{"c":"c1"}}}}""")

    server.executeQuerySimple(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
      """{"data":{"children":[{"c":"c1","parentsOpt":[{"p":"p1"},{"p":"p2"}]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(2))
  }

  "a P1 to CM  relation with the child not already in a relation" should "be connectable through a nested mutation by unique" ignore {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToManyRelation("parentsOpt", "childOpt", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createChild(data: {c: "c1"}){
        |       c
        |  }
        |}""".stripMargin,
      project
    )

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {p: "p1"}){
        |       p
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: {p: "p1"}
         |  data:{
         |    childOpt: {connect: {c: "c1"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childOpt":{"c":"c1"}}}}""")

    server.executeQuerySimple(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
      """{"data":{"children":[{"c":"c1","parentsOpt":[{"p":"p1"}]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a PM to CM  relation with the children already in a relation" should "be connectable through a nested mutation by unique" ignore {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).manyToManyRelation("parentsOpt", "childrenOpt", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
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

    server.executeQuerySimple(
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

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(4))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: {    p: "p2"}
         |  data:{
         |    childrenOpt: {connect: [{c: "c1"}, {c: "c2"}]}
         |  }){
         |    childrenOpt{
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"},{"c":"c3"},{"c":"c4"}]}}}""")

    server.executeQuerySimple(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
      """{"data":{"children":[{"c":"c1","parentsOpt":[{"p":"p1"},{"p":"p2"}]},{"c":"c2","parentsOpt":[{"p":"p1"},{"p":"p2"}]},{"c":"c3","parentsOpt":[{"p":"p2"}]},{"c":"c4","parentsOpt":[{"p":"p2"}]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(6))
  }

  "a PM to CM  relation with the child not already in a relation" should "be connectable through a nested mutation by unique" ignore {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToManyRelation("parentsOpt", "childOpt", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createChild(data: {c: "c1"}){
        |       c
        |  }
        |}""".stripMargin,
      project
    )

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {p: "p1"}){
        |       p
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: {p: "p1"}
         |  data:{
         |    childOpt: {connect: {c: "c1"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childOpt":{"c":"c1"}}}}""")

    server.executeQuerySimple(s"""query{children{parentsOpt{p}}}""", project).toString should be("""{"data":{"children":[{"parentsOpt":[{"p":"p1"}]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a one to many relation" should "be creatable through a nested mutation" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field_!("text", _.String)
      schema.model("Todo").oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    val result = server.executeQuerySimple(
      """
        |mutation {
        |  createTodo(data:{
        |    comments: {
        |      create: [{text: "comment1"}, {text: "comment2"}]
        |    }
        |  }){
        |    id
        |    comments {
        |      text
        |    }
        |  }
        |}
      """.stripMargin,
      project
    )
    mustBeEqual(result.pathAsJsValue("data.createTodo.comments").toString, """[{"text":"comment1"},{"text":"comment2"}]""")
  }

  "a many to one relation" should "be creatable through a nested mutation" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field_!("text", _.String)
      schema.model("Todo").field_!("title", _.String).oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    val result = server.executeQuerySimple(
      """
        |mutation {
        |  createComment(data: {
        |    text: "comment1"
        |    todo: {
        |      create: {title: "todo1"}
        |    }
        |  }){
        |    id
        |    todo {
        |      title
        |    }
        |  }
        |}
      """.stripMargin,
      project
    )
    mustBeEqual(result.pathAsString("data.createComment.todo.title"), "todo1")
  }

  "a many to many relation" should "creatable through a nested mutation" in {
    val project = SchemaDsl() { schema =>
      val tag = schema.model("Tag").field_!("name", _.String)
      schema.model("Todo").field_!("title", _.String).manyToManyRelation("tags", "todos", tag)
    }
    database.setup(project)

    val result = server
      .executeQuerySimple(
        """
        |mutation {
        |  createTodo(data:{
        |    title: "todo1"
        |    tags: {
        |      create: [{name: "tag1"}, {name: "tag2"}]
        |    }
        |  }){
        |    id
        |    tags {
        |      name
        |    }
        |  }
        |}
      """.stripMargin,
        project
      )

    mustBeEqual(result.pathAsJsValue("data.createTodo.tags").toString, """[{"name":"tag1"},{"name":"tag2"}]""")

    val result2 = server.executeQuerySimple(
      """
        |mutation {
        |  createTag(data:{
        |    name: "tag1"
        |    todos: {
        |      create: [{title: "todo1"}, {title: "todo2"}]
        |    }
        |  }){
        |    id
        |    todos {
        |      title
        |    }
        |  }
        |}
      """.stripMargin,
      project
    )
    mustBeEqual(result2.pathAsJsValue("data.createTag.todos").toString, """[{"title":"todo1"},{"title":"todo2"}]""")
  }

  "A nested create on a one to one relation" should "correctly assign violations to offending model and not partially execute" in {
    val project = SchemaDsl() { schema =>
      val user = schema.model("User").field_!("name", _.String).field("unique", _.String, isUnique = true)
      schema.model("Post").field_!("title", _.String).field("uniquePost", _.String, isUnique = true).oneToOneRelation("user", "post", user)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation{
        |  createUser(data:{
        |    name: "Paul"
        |    unique: "uniqueUser"
        |    post: {create:{title: "test"    uniquePost: "uniquePost"}
        |    }
        |  })
        |    {id}
        |  }
      """.stripMargin,
      project
    )

    server.executeQuerySimple("query{users{id}}", project).pathAsSeq("data.users").length should be(1)
    server.executeQuerySimple("query{posts{id}}", project).pathAsSeq("data.posts").length should be(1)

    server.executeQuerySimpleThatMustFail(
      """mutation{
        |  createUser(data:{
        |    name: "Paul2"
        |    unique: "uniqueUser"
        |    post: {create:{title: "test2"    uniquePost: "uniquePost2"}
        |    }
        |  })
        |    {id}
        |  }
      """.stripMargin,
      project,
      errorCode = 3010,
      errorContains = "A unique constraint would be violated on User. Details: Field name = unique"
    )

    server.executeQuerySimple("query{users{id}}", project).pathAsSeq("data.users").length should be(1)
    server.executeQuerySimple("query{posts{id}}", project).pathAsSeq("data.posts").length should be(1)

    server.executeQuerySimpleThatMustFail(
      """mutation{
        |  createUser(data:{
        |    name: "Paul2"
        |    unique: "uniqueUser2"
        |    post: {create:{title: "test2"    uniquePost: "uniquePost"}
        |    }
        |  })
        |    {id}
        |  }
      """.stripMargin,
      project,
      errorCode = 3010,
      errorContains = "A unique constraint would be violated on Post. Details: Field name = uniquePost"
    )

    server.executeQuerySimple("query{users{id}}", project).pathAsSeq("data.users").length should be(1)
    server.executeQuerySimple("query{posts{id}}", project).pathAsSeq("data.posts").length should be(1)
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

    val mutation =
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

    val result = server.executeQuerySimple(mutation, project)
    result.pathAsString("data.createList.name") should equal("the list")
    result.pathAsString("data.createList.todos.[0].title") should equal("the todo")
    result.pathAsString("data.createList.todos.[0].tag.name") should equal("the tag")
  }

  "a required one2one relation" should "be creatable through a nested create mutation" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field_!("reqOnComment", _.String).field("optOnComment", _.String)
      schema.model("Todo").field_!("reqOnTodo", _.String).field("optOnTodo", _.String).oneToOneRelation_!("comments", "todo", comment)
    }
    database.setup(project)

    val result = server.executeQuerySimple(
      """
        |mutation {
        |  createComment(data: {
        |    reqOnComment: "comment1"
        |    todo: {
        |      create: {reqOnTodo: "todo1"}
        |    }
        |  }){
        |    id
        |    todo{reqOnTodo}
        |  }
        |}
      """.stripMargin,
      project
    )
    mustBeEqual(result.pathAsString("data.createComment.todo.reqOnTodo"), "todo1")

    server.executeQuerySimpleThatMustFail(
      """
        |mutation {
        |  createComment(data: {
        |    reqOnComment: "comment1"
        |    todo: {}
        |  }){
        |    id
        |    todo {
        |      reqOnTodo
        |    }
        |  }
        |}
      """.stripMargin,
      project,
      errorCode = 3032,
      errorContains = "The field 'todo' on type 'Comment' is required. Performing this mutation would violate that constraint"
    )
  }

  "a required one2one relation" should "be creatable through a nested connected mutation" in {
    val project = SchemaDsl() { schema =>
      val todo = schema.model("Todo").field_!("reqOnTodo", _.String).field("optOnTodo", _.String)
      schema
        .model("Comment")
        .field_!("reqOnComment", _.String)
        .field("optOnComment", _.String)
        .oneToOneRelation_!("todo", "comment", todo, isRequiredOnOtherField = false)
    }
    database.setup(project)

    val result = server.executeQuerySimple(
      """
        |mutation {
        |  createComment(data: {
        |    reqOnComment: "comment1"
        |    todo: {
        |      create: {reqOnTodo: "todo1"}
        |    }
        |  }){
        |    id
        |    todo{
        |       reqOnTodo
        |    }
        |  }
        |}
      """.stripMargin,
      project
    )
    mustBeEqual(result.pathAsString("data.createComment.todo.reqOnTodo"), "todo1")

    server.executeQuerySimple("{ todoes { id } }", project).pathAsSeq("data.todoes").size should be(1)
    server.executeQuerySimple("{ comments { id } }", project).pathAsSeq("data.comments").size should be(1)

    server.executeQuerySimpleThatMustFail(
      """
        |mutation {
        |  createComment(data: {
        |    reqOnComment: "comment1"
        |    todo: {}
        |  }){
        |    id
        |    todo {
        |      reqOnTodo
        |    }
        |  }
        |}
      """.stripMargin,
      project,
      errorCode = 3032,
      errorContains = "The field 'todo' on type 'Comment' is required. Performing this mutation would violate that constraint"
    )

    server.executeQuerySimple("{ todoes { id } }", project).pathAsSeq("data.todoes").size should be(1)
    server.executeQuerySimple("{ comments { id } }", project).pathAsSeq("data.comments").size should be(1)

    val todoId = server
      .executeQuerySimple(
        """
        |mutation {
        |  createTodo(data: {
        |       reqOnTodo: "todo2"
        |       }
        |       )
        |  {id}
        |}
      """.stripMargin,
        project
      )
      .pathAsString("data.createTodo.id")

    server.executeQuerySimple("{ todoes { id } }", project).pathAsSeq("data.todoes").size should be(2)
    server.executeQuerySimple("{ comments { id } }", project).pathAsSeq("data.comments").size should be(1)

    server.executeQuerySimple(
      s"""
        |mutation {
        |  createComment(data: {
        |    reqOnComment: "comment1"
        |    todo: {
        |      connect: {id: "$todoId"}
        |    }
        |  }){
        |    id
        |    todo{
        |       reqOnTodo
        |    }
        |  }
        |}
      """.stripMargin,
      project
    )

    server.executeQuerySimple("{ todoes { id } }", project).pathAsSeq("data.todoes").size should be(2)
    server.executeQuerySimple("{ comments { id } }", project).pathAsSeq("data.comments").size should be(2)

  }

}
