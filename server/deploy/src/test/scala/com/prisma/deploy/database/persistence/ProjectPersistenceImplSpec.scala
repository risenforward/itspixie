package com.prisma.deploy.database.persistence

import com.prisma.deploy.database.tables.Tables
import com.prisma.deploy.specutils.{DeploySpecBase, TestProject}
import com.prisma.shared.models.{Migration, MigrationId, MigrationStatus}
import org.scalatest.{FlatSpec, Matchers}
import slick.jdbc.MySQLProfile.api._

class ProjectPersistenceImplSpec extends FlatSpec with Matchers with DeploySpecBase {

  val projectPersistence   = testDependencies.projectPersistence
  val migrationPersistence = testDependencies.migrationPersistence

  ".load()" should "return None if there's no project yet in the database" in {
    val result = projectPersistence.load("non-existent-id@some-stage").await()
    result should be(None)
  }

  ".load()" should "return the project with the correct revision" in {
    val (project, _) = setupProject(basicTypesGql)

    // Create an empty migration to have an unapplied migration with a higher revision
    migrationPersistence.create(Migration.empty(project.id)).await

    def loadProject = {
      val result = projectPersistence.load(project.id).await()
      result shouldNot be(None)
      result
    }

    // Only load the applied revision, which is 2 (setup does add + deploy = revisions 0, 1)
    loadProject.get.revision shouldEqual 2

    // After another migration is completed, the revision is bumped to the revision of the latest migration
    migrationPersistence.updateMigrationStatus(MigrationId(project.id, 3), MigrationStatus.Success).await
    loadProject.get.revision shouldEqual 3
  }

  ".create()" should "store the project in the db" in {
    assertNumberOfRowsInProjectTable(0)
    projectPersistence.create(TestProject()).await()
    assertNumberOfRowsInProjectTable(1)
  }

  ".loadAll()" should "load all projects (for a user TODO)" in {
    setupProject(basicTypesGql)
    setupProject(basicTypesGql)

    projectPersistence.loadAll().await should have(size(2))
  }

  ".update()" should "update a project" in {
    val (project, _) = setupProject(basicTypesGql)

    val updatedProject = project.copy(secrets = Vector("Some", "secrets"))
    projectPersistence.update(updatedProject).await()

    val reloadedProject = projectPersistence.load(project.id).await.get
    reloadedProject.secrets should contain allOf (
      "Some",
      "secrets"
    )
  }

  def assertNumberOfRowsInProjectTable(count: Int): Unit = {
    val query = Tables.Projects.size
    internalDb.run(query.result) should equal(count)
  }
}
