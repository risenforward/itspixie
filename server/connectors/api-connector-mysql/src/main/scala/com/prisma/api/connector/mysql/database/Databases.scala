package com.prisma.api.connector.mysql.database

import com.prisma.config.DatabaseConfig
import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.MySQLProfile.backend.DatabaseDef

case class Databases(master: DatabaseDef, readOnly: DatabaseDef)

object Databases {
  private lazy val dbDriver = new org.mariadb.jdbc.Driver
//  private val configRoot    = "clientDatabases"

  def initialize(dbConfig: DatabaseConfig): Databases = {
//    import scala.collection.JavaConverters._
    val config   = typeSafeConfigFromDatabaseConfig(dbConfig)
    val masterDb = Database.forConfig("database", config, driver = dbDriver)
    val dbs = Databases(
      master = masterDb,
      readOnly = masterDb //if (config.hasPath(readOnlyPath)) readOnlyDb else masterDb
    )

//    val databasesMap = for {
//      dbName <- asScalaSet(config.getObject(configRoot).keySet())
//    } yield {
////      val readOnlyPath = s"$configRoot.$dbName.readonly"
//      val masterDb = Database.forConfig("database", config, driver = dbDriver)
////      lazy val readOnlyDb = Database.forConfig(readOnlyPath, config, driver = dbDriver)
//
//      dbName -> dbs
//    }

    dbs
  }

  def typeSafeConfigFromDatabaseConfig(dbConfig: DatabaseConfig): Config = {
    ConfigFactory
      .parseString(s"""
        |database {
        |  connectionInitSql="set names utf8mb4"
        |  dataSourceClass = "slick.jdbc.DriverDataSource"
        |  properties {
        |    url = "jdbc:mysql://${dbConfig.host}:${dbConfig.port}/?autoReconnect=true&useSSL=false&serverTimeZone=UTC&useUnicode=true&characterEncoding=UTF-8&socketTimeout=60000&usePipelineAuth=false"
        |    user = ${dbConfig.user}
        |    password = ${dbConfig.password}
        |  }
        |  numThreads = ${dbConfig.connectionLimit.getOrElse(10)}
        |  connectionTimeout = 5000
        |}
      """.stripMargin)
      .resolve
  }
}
