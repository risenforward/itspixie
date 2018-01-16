package com.prisma.deploy.database.schema.queries

import com.prisma.deploy.specutils.DeploySpecBase
import com.prisma.shared.models.ProjectId
import org.scalatest.{FlatSpec, Matchers}

class ListMigrationsSpec extends FlatSpec with Matchers with DeploySpecBase {

  "ListMigrations" should "return all migrations for a project" in {
    val (project, _) = setupProject(basicTypesGql)
    val nameAndStage = ProjectId.fromEncodedString(project.id)
    val result       = server.query(s"""
       |query {
       |  listMigrations(name: "${nameAndStage.name}", stage: "${nameAndStage.stage}") {
       |    projectId
       |    revision
       |    status
       |    applied
       |    errors
       |    steps {
       |      type
       |    }
       |  }
       |}
      """.stripMargin)

    val list = result.pathAsSeq("data.listMigrations")
    list should have(size(2))
  }
}
