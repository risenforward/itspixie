package com.prisma.api.mutations

import com.prisma.api.ApiSpecBase
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class NestedConnectMutationInsideUpsertSpec extends FlatSpec with Matchers with ApiSpecBase {

  "a one to many relation" should "be connectable by id within an upsert in the create case" ignore {
    val project = SchemaDsl.fromBuilder { schema =>
      val customer = schema.model("Customer").field_!("name", _.String)
      schema.model("Tenant").field_!("name", _.String).oneToManyRelation("customers", "tenant", customer)
    }
    database.setup(project)

    val tenantId = server.query("""mutation { createTenant(data: {name:"Gustav G"}){ id } }""", project).pathAsString("data.createTenant.id")

    val result = server.query(
      s"""mutation{upsertCustomer(where: {id: "DOESNOTEXIST"}, create: {name: "Paul P", tenant:{connect:{id:"$tenantId"}}}, update: {name: "Paul P"}) {
         |    tenant{name}
         |  }
         |}
      """.stripMargin,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.upsertCustomer.tenant").toString, """{"name":"Gustav G"}""")
  }

  "a one to many relation" should "be connectable by id within an upsert in the update case" ignore {
    val project = SchemaDsl.fromBuilder { schema =>
      val customer = schema.model("Customer").field_!("name", _.String)
      schema.model("Tenant").field_!("name", _.String).oneToManyRelation("customers", "tenant", customer)
    }
    database.setup(project)

    val tenantId   = server.query("""mutation { createTenant(data: {name:"Gustav G"}){ id } }""", project).pathAsString("data.createTenant.id")
    val customerId = server.query("""mutation { createCustomer(data: {name:"Paul P"}){ id } }""", project).pathAsString("data.createCustomer.id")

    val result = server.query(
      s"""mutation{upsertCustomer(where: {id: "$customerId"}, create: {name: "Bernd B"}, update: {name: "Bernd B",tenant:{connect:{id:"$tenantId"}}}) {
         |    tenant{name}
         |  }
         |}
      """.stripMargin,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.upsertCustomer.tenant").toString, """{"name":"Gustav G"}""")
  }

  "a one to many relation" should "be connectable by unique field within an upsert in the update case" ignore {
    val project = SchemaDsl.fromBuilder { schema =>
      val customer = schema.model("Customer").field_!("name", _.String, isUnique = true)
      schema.model("Tenant").field_!("name", _.String, isUnique = true).oneToManyRelation("customers", "tenant", customer)
    }
    database.setup(project)

    server.query("""mutation { createTenant(data: {name:"Gustav G"}){ id } }""", project)
    server.query("""mutation { createCustomer(data: {name:"Paul P"}){ id } }""", project)

    val result = server.query(
      s"""mutation{upsertCustomer(where: {name: "Paul P"}, create: {name: "Bernd B"}, update: {name: "Bernd B",tenant:{connect:{name:"Gustav G"}}}) {
         |    tenant{name}
         |  }
         |}
      """.stripMargin,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.upsertCustomer.tenant").toString, """{"name":"Gustav G"}""")
  }

  //other direction

}
