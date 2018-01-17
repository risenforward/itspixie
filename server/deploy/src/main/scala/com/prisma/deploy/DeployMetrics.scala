package com.prisma.deploy

import com.prisma.errors.{BugsnagErrorReporter, ErrorReporter}
import com.prisma.metrics.MetricsManager
import com.prisma.profiling.MemoryProfiler

object DeployMetrics extends MetricsManager {
  // this is intentionally empty. Since we don't define metrics here, we need to load the object once so the profiler kicks in.
  // This way it does not look so ugly on the caller side.
  def init(reporter: ErrorReporter): Unit = {}

  implicit val reporter = BugsnagErrorReporter(sys.env.getOrElse("BUGSNAG_API_KEY", ""))

  // CamelCase the service name read from env
  override def serviceName =
    sys.env
      .getOrElse("SERVICE_NAME", "SystemShared")
      .split("-")
      .map { x =>
        x.head.toUpper + x.tail
      }
      .mkString

  MemoryProfiler.schedule(this)
}
