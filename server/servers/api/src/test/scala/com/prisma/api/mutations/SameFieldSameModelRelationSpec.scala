package com.prisma.api.mutations

import com.prisma.IgnorePassive
import com.prisma.api.ApiSpecBase
import com.prisma.shared.models.Project
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class SameFieldSameModelRelationSpec extends FlatSpec with Matchers with ApiSpecBase {

  "A Many to Many Self Relation" should "be accessible from both sides" in {
    val project: Project = SchemaDsl.fromString() { """type Post {
                                                      |  id: ID! @unique
                                                      |  identifier: Int @unique
                                                      |  related: [Post!]! @relation(name: "RelatedPosts")
                                                      |}""" }

    database.setup(project)

    server.query("mutation{createPost(data:{identifier: 1}){identifier}}", project)
    server.query("mutation{createPost(data:{identifier: 2}){identifier}}", project)

    server.query(
      """mutation {
                   |  updatePost (
                   |  
                   |    where:{identifier: 1}
                   |    data: {
                   |      related: {
                   |        connect: {
                   |          identifier: 2
                   |        }
                   |      }
                   |    }
                   |  ) {
                   |    identifier
                   |  }
                   |}""",
      project
    )

    server.query("{post(where:{identifier: 1}){identifier, related{identifier}}}", project).toString should be(
      """{"data":{"post":{"identifier":1,"related":[{"identifier":2}]}}}""")
    server.query("{post(where:{identifier: 2}){identifier, related{identifier}}}", project).toString should be(
      """{"data":{"post":{"identifier":2,"related":[{"identifier":1}]}}}""")
  }

  // taggedAs (IgnorePassive)
  "A One to One Self Relation" should "be accessible from both sides" in {
    val project: Project = SchemaDsl.fromString() { """type Post {
                                                      |  id: ID! @unique
                                                      |  identifier: Int @unique
                                                      |  related: Post @relation(name: "RelatedPosts")
                                                      |}""" }

    database.setup(project)

    server.query("mutation{createPost(data:{identifier: 1}){identifier}}", project)
    server.query("mutation{createPost(data:{identifier: 2}){identifier}}", project)

    server.query(
      """mutation {
        |  updatePost (
        |  
        |    where:{identifier: 1}
        |    data: {
        |      related: {
        |        connect: {
        |          identifier: 2
        |        }
        |      }
        |    }
        |  ) {
        |    identifier
        |  }
        |}""",
      project
    )

    server.query("{post(where:{identifier: 1}){identifier, related{identifier}}}", project).toString should be(
      """{"data":{"post":{"identifier":1,"related":{"identifier":2}}}}""")
    server.query("{post(where:{identifier: 2}){identifier, related{identifier}}}", project).toString should be(
      """{"data":{"post":{"identifier":2,"related":{"identifier":1}}}}""")

  }
}
