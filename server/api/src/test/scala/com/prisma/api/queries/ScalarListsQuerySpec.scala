package com.prisma.api.queries

import com.prisma.api.ApiBaseSpec
import com.prisma.shared.models.Project
import com.prisma.shared.project_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class ScalarListsQuerySpec extends FlatSpec with Matchers with ApiBaseSpec {

  "empty scalar list" should "return empty list" in {

    val project = SchemaDsl() { schema =>
      schema.model("Model").field("ints", _.Int, isList = true).field("strings", _.String, isList = true)
    }

    database.setup(project)

    val id = server
      .executeQuerySimple(
        s"""mutation {
           |  createModel(data: {strings: { set: [] }}) {
           |    id
           |    strings
           |  }
           |}""".stripMargin,
        project
      )
      .pathAsString("data.createModel.id")

    val result = server.executeQuerySimple(
      s"""{
         |  model(where: {id:"$id"}) {
         |    ints
         |    strings
         |  }
         |}""".stripMargin,
      project
    )

    result.toString should equal("""{"data":{"model":{"ints":[],"strings":[]}}}""")
  }

  "full scalar list" should "return full list" in {

    val project = SchemaDsl() { schema =>
      schema.model("Model").field("ints", _.Int, isList = true).field("strings", _.String, isList = true)
    }

    database.setup(project)

    val id = server
      .executeQuerySimple(
        s"""mutation {
           |  createModel(data: {ints: { set: [1] }, strings: { set: ["short", "looooooooooong"]}}) {
           |    id
           |    strings
           |    ints
           |  }
           |}""".stripMargin,
        project
      )
      .pathAsString("data.createModel.id")

    val result = server.executeQuerySimple(
      s"""{
         |  model(where: {id:"$id"}) {
         |    ints
         |    strings
         |  }
         |}""".stripMargin,
      project
    )

    result.toString should equal("""{"data":{"model":{"ints":[1],"strings":["short","looooooooooong"]}}}""")
  }

  "full scalar list" should "preserve order of elements" in {

    val project = SchemaDsl() { schema =>
      schema.model("Model").field("ints", _.Int, isList = true).field("strings", _.String, isList = true)
    }
    database.setup(project)

    val id = server
      .executeQuerySimple(
        s"""mutation {
           |  createModel(data: {ints: { set: [1,2] }, strings: { set: ["short", "looooooooooong"] }}) {
           |    id
           |  }
           |}""".stripMargin,
        project
      )
      .pathAsString("data.createModel.id")

    server
      .executeQuerySimple(
        s"""mutation {
           |  updateModel(where: {id: "$id"} data: {ints: { set: [2,1] }}) {
           |    id
           |    ints
           |    strings
           |  }
           |}""".stripMargin,
        project
      )

    val result = server.executeQuerySimple(
      s"""{
         |  model(where: {id:"$id"}) {
         |    ints
         |    strings
         |  }
         |}""".stripMargin,
      project
    )

    result.toString should equal("""{"data":{"model":{"ints":[2,1],"strings":["short","looooooooooong"]}}}""")
  }

  //----------------------------TYPES-----------------------------

  "full scalar list" should "return full list for strings" in {

    val fieldName   = "strings"
    val inputValue  = """"STRING""""
    val outputValue = """"STRING""""

    val project = SchemaDsl() { schema =>
      schema.model("Model").field(fieldName, _.String, isList = true)
    }

    verifySuccessfulSetAndRetrieval(fieldName, inputValue, outputValue, project)
  }

  "full scalar list" should "return full list for ints" in {

    val fieldName   = "ints"
    val inputValue  = 1
    val outputValue = 1

    val project = SchemaDsl() { schema =>
      schema.model("Model").field(fieldName, _.Int, isList = true)
    }

    verifySuccessfulSetAndRetrieval(fieldName, inputValue, outputValue, project)
  }

  "full scalar list" should "return full list for floats" in {

    val fieldName   = "floats"
    val inputValue  = 1.345
    val outputValue = 1.345

    val project = SchemaDsl() { schema =>
      schema.model("Model").field(fieldName, _.Float, isList = true)
    }

    verifySuccessfulSetAndRetrieval(fieldName, inputValue, outputValue, project)
  }

  "full scalar list" should "return full list for booleans" in {

    val fieldName   = "booleans"
    val inputValue  = true
    val outputValue = true

    val project = SchemaDsl() { schema =>
      schema.model("Model").field(fieldName, _.Boolean, isList = true)
    }

    verifySuccessfulSetAndRetrieval(fieldName, inputValue, outputValue, project)
  }

  "full scalar list" should "return full list for GraphQLIds" in {

    val fieldName   = "graphqlids"
    val inputValue  = """"someID123""""
    val outputValue = """"someID123""""

    val project = SchemaDsl() { schema =>
      schema.model("Model").field(fieldName, _.GraphQLID, isList = true)
    }

    verifySuccessfulSetAndRetrieval(fieldName, inputValue, outputValue, project)
  }

  "full scalar list" should "return full list for json" ignore {

    val fieldName   = "jsons"
    val inputValue  = """"{\"a\":2}""""
    val outputValue = """{"a":"b"}"""

    val project = SchemaDsl() { schema =>
      schema.model("Model").field(fieldName, _.Json, isList = true)
    }

    verifySuccessfulSetAndRetrieval(fieldName, inputValue, outputValue, project)
  }

  "full scalar list" should "return full list for datetime" in {

    val fieldName   = "datetimes"
    val inputValue  = """"2018""""
    val outputValue = """"2018-01-01T00:00:00.000Z""""

    val project = SchemaDsl() { schema =>
      schema.model("Model").field(fieldName, _.DateTime, isList = true)
    }

    verifySuccessfulSetAndRetrieval(fieldName, inputValue, outputValue, project)
  }

  "full scalar list" should "return full list for enum" in {

    val fieldName   = "enum"
    val inputValue  = "HA"
    val outputValue = """"HA""""

    val project = SchemaDsl() { schema =>
      val enum = schema.enum("HA", Vector("HA", "HI"))
      schema.model("Model").field(fieldName, _.Enum, isList = true, enum = Some(enum))
    }

    verifySuccessfulSetAndRetrieval(fieldName, inputValue, outputValue, project)
  }

  private def verifySuccessfulSetAndRetrieval(fieldName: String, inputValue: Any, outputValue: Any, project: Project) = {
    database.setup(project)

    val id = server
      .executeQuerySimple(
        s"""mutation {
           |  createModel(data: {$fieldName: { set: [$inputValue] }}) {
           |    id
           |    $fieldName
           |  }
           |}""".stripMargin,
        project
      )
      .pathAsString("data.createModel.id")

    val result = server.executeQuerySimple(
      s"""{
         |  model(where: {id:"$id"}) {
         |    $fieldName
         |  }
         |}""".stripMargin,
      project
    )

    result.toString should equal(s"""{"data":{"model":{"$fieldName":[$outputValue]}}}""")
  }
}
