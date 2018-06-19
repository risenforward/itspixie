package com.prisma.deploy.database.schema.mutations

import com.prisma.deploy.specutils.DeploySpecBase
import org.scalatest.{FlatSpec, Matchers}

class SetCloudSecretMutationSpec extends FlatSpec with Matchers with DeploySpecBase {

  val secretLoader = testDependencies.deployConnector.cloudSecretLoader

  "setCloudSecret" should "succeed" in {
    val (project, _) = setupProject(basicTypesGql)

    secretLoader.loadCloudSecret().await() should be(None)

    server.executeQuery("""
        |mutation {
        |  setCloudSecret(input: {secret: "foo"}){
        |    clientMutationId
        |  }
        |}
      """.stripMargin)

    secretLoader.loadCloudSecret().await() should be(Some("foo"))

    server.executeQuery("""
                          |mutation {
                          |  setCloudSecret(input: {secret: "bar"}){
                          |    clientMutationId
                          |  }
                          |}
                        """.stripMargin)

    secretLoader.loadCloudSecret().await() should be(Some("bar"))
  }
}
