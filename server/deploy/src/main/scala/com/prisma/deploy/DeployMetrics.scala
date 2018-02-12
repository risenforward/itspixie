package com.prisma.deploy

import akka.actor.Actor
import com.prisma.akkautil.LogUnhandled
import com.prisma.deploy.DatabaseSizeReporter.{DatabaseSize, Report}
import com.prisma.deploy.database.persistence.ProjectPersistence
import com.prisma.errors.{BugsnagErrorReporter, ErrorReporter}
import com.prisma.metrics.{CustomTag, GaugeMetric, MetricsManager}
import com.prisma.profiling.{JvmProfiler, MemoryProfiler}
import com.prisma.shared.models.Project
import slick.jdbc
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.collection.mutable
import scala.concurrent.Future

object DeployMetrics extends MetricsManager(BugsnagErrorReporter(sys.env.getOrElse("BUGSNAG_API_KEY", ""))) {
  // this is intentionally empty. Since we don't define metrics here, we need to load the object once so the profiler kicks in.
  // This way it does not look so ugly on the caller side.
  def init(reporter: ErrorReporter): Unit = {}

  // CamelCase the service name read from env
  override def serviceName =
    sys.env
      .getOrElse("SERVICE_NAME", "Deploy")
      .split("-")
      .map { x =>
        x.head.toUpper + x.tail
      }
      .mkString

  JvmProfiler.schedule(this)
}

object DatabaseSizeReporter {
  object Report

  case class DatabaseSize(name: String, total: Double)
}
case class DatabaseSizeReporter(
    projectPersistence: ProjectPersistence,
    database: jdbc.MySQLProfile.backend.Database
) extends Actor
    with LogUnhandled {
  import scala.concurrent.duration._
  import context.dispatcher

  scheduleReport()

  val projectIdTag = CustomTag("projectId")
  val gauges       = mutable.Map.empty[String, GaugeMetric]

  override def receive = logUnhandled {
    case Report =>
      for {
        projects      <- projectPersistence.loadAll()
        databaseSizes <- getAllDatabaseSizes()
      } yield {
        projects.foreach { project =>
          databaseSizes.find(_.name == project.id).foreach { dbSize =>
            val gauge = gaugeForProject(project)
            gauge.set(dbSize.total.toLong)
          }
        }
        scheduleReport()
      }
  }

  def scheduleReport() = context.system.scheduler.scheduleOnce(5.seconds, self, Report)

  def gaugeForProject(project: Project): GaugeMetric = {
    // these Metrics are consumed by the console to power the dashboard. Only change them with extreme caution!
    gauges.getOrElseUpdate(project.id, {
      DeployMetrics.defineGauge("databaseSize", (projectIdTag, project.id))
    })
  }

  def getAllDatabaseSizes(): Future[Vector[DatabaseSize]] = database.run(action)

  val action = {
    val query = sql"""
         SELECT table_schema, sum( data_length + index_length) / 1024 / 1024 FROM information_schema.TABLES GROUP BY table_schema
       """
    query.as[(String, Double)].map { tuples =>
      tuples.map { tuple =>
        DatabaseSize(tuple._1, tuple._2)
      }
    }
  }
}
