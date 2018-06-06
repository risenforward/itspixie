package com.prisma.auth

import com.prisma.config.{ConfigLoader, InvalidConfiguration}
import org.scalatest.{Matchers, WordSpec}

class ConfigLoaderSpec extends WordSpec with Matchers {
  "a valid config" should {
    "be parsed without errors" in {
      val validConfig = """
                          |port: 4466
                          |managementApiSecret: somesecret
                          |prismaConnectSecret: othersecret
                          |databases:
                          |  default:
                          |    connector: mysql
                          |    migrations: true
                          |    host: localhost
                          |    port: 3306
                          |    user: root
                          |    password: prisma
                          |    database: my_database
                          |    schema: my_schema
                        """.stripMargin

      val config = ConfigLoader.tryLoadString(validConfig)

      config.isSuccess shouldBe true
      config.get.port shouldBe Some(4466)
      config.get.managementApiSecret should contain("somesecret")
      config.get.prismaConnectSecret should contain("othersecret")
      config.get.databases.length shouldBe 1
      config.get.databases.head.connector shouldBe "mysql"
      config.get.databases.head.active shouldBe true
      config.get.databases.head.port shouldBe 3306
      config.get.databases.head.user shouldBe "root"
      config.get.databases.head.password shouldBe Some("prisma")
      config.get.databases.head.database shouldBe Some("my_database")
      config.get.databases.head.schema shouldBe Some("my_schema")
    }

    "be parsed without errors if an optional field is missing" in {
      val validConfig = """
                          |port: 4466
                          |databases:
                          |  default:
                          |    connector: mysql
                          |    host: localhost
                          |    port: 3306
                          |    user: root
                          |    password: prisma
                        """.stripMargin

      val config = ConfigLoader.tryLoadString(validConfig)

      config.isSuccess shouldBe true
      config.get.port should contain(4466)
      config.get.managementApiSecret shouldBe None
      config.get.databases.length shouldBe 1
      config.get.databases.head.connector shouldBe "mysql"
      config.get.databases.head.active shouldBe true
      config.get.databases.head.port shouldBe 3306
      config.get.databases.head.user shouldBe "root"
      config.get.databases.head.password shouldBe Some("prisma")
      config.get.databases.head.database shouldBe None
      config.get.databases.head.schema shouldBe None
    }

    "be parsed without errors if an optional field is there but set to nothing" in {
      val validConfig = """
                          |port: 4466
                          |managementApiSecret:
                          |databases:
                          |  default:
                          |    connector: mysql
                          |    migrations: true
                          |    host: localhost
                          |    port: 3306
                          |    user: root
                          |    password: prisma
                          |    database:
                          |    schema:
                        """.stripMargin

      val config = ConfigLoader.tryLoadString(validConfig)

      config.isSuccess shouldBe true
      config.get.port should contain(4466)
      config.get.managementApiSecret shouldBe None
      config.get.databases.length shouldBe 1
      config.get.databases.head.connector shouldBe "mysql"
      config.get.databases.head.active shouldBe true
      config.get.databases.head.port shouldBe 3306
      config.get.databases.head.user shouldBe "root"
      config.get.databases.head.password shouldBe Some("prisma")
      config.get.databases.head.database shouldBe None
      config.get.databases.head.schema shouldBe None
    }
  }

  "an invalid config" should {
    "fail with an invalid config format error for an invalid int conversion" ignore {
      val invalidConfig = """
                            |port: Invalid
                            |managementApiSecret: somesecret
                            |databases:
                            |  default:
                            |    connector: mysql
                            |    migrations: true
                            |    host: localhost
                            |    port: 3306
                            |    user: root
                            |    password: prisma
                          """.stripMargin

      val config = ConfigLoader.tryLoadString(invalidConfig)

      config.isSuccess shouldBe false
      config.failed.get shouldBe a[InvalidConfiguration]
    }
  }

  "fail with an invalid config format error for an invalid boolean conversion" in {
    val invalidConfig = """
                          |port: 4466
                          |managementApiSecret: somesecret
                          |databases:
                          |  default:
                          |    connector: mysql
                          |    host: localhost
                          |    port: notanumber
                          |    user: root
                          |    password: prisma
                        """.stripMargin

    val config = ConfigLoader.tryLoadString(invalidConfig)

    config.isSuccess shouldBe false
    config.failed.get shouldBe a[InvalidConfiguration]
  }

  "fail with an invalid config format error for a missing top level field" in {
    val invalidConfig = """
                          |port: 4466
                          |managementApiSecret: somesecret
                        """.stripMargin

    val config = ConfigLoader.tryLoadString(invalidConfig)

    config.isSuccess shouldBe false
    config.failed.get shouldBe a[InvalidConfiguration]
  }
}
