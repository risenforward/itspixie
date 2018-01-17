package com.prisma.api.import_export

import com.prisma.api.ApiBaseSpec
import com.prisma.api.database.DataResolver
import com.prisma.api.database.import_export.BulkExport
import com.prisma.api.database.import_export.ImportExport.MyJsonProtocol._
import com.prisma.api.database.import_export.ImportExport.{Cursor, ExportRequest, JsonBundle, ResultFormat}
import com.prisma.shared.models.Project
import com.prisma.shared.project_dsl.SchemaDsl
import com.prisma.utils.await.AwaitUtils
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

class BulkExportIncompleteSchemaSpec extends FlatSpec with Matchers with ApiBaseSpec with AwaitUtils {

  val project: Project = SchemaDsl() { schema =>
    }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    database.setup(project)
  }

  override def beforeEach(): Unit = {
    database.truncate(project)
  }

  val exporter                   = new BulkExport(project)
  val dataResolver: DataResolver = this.dataResolver(project)
  val start                      = Cursor(0, 0, 0, 0)
  val emptyResult                = ResultFormat(JsonBundle(Vector.empty, 0), Cursor(-1, -1, -1, -1), isFull = false)

  "Exporting nodes" should "fail gracefully if no models are defined" in {
    val request = ExportRequest("nodes", start)
    exporter.executeExport(dataResolver, request.toJson).await(5).convertTo[ResultFormat] should be(emptyResult)
  }

  "Exporting lists" should "fail gracefully if no lists are defined" in {
    val request = ExportRequest("lists", start)
    exporter.executeExport(dataResolver, request.toJson).await(5).convertTo[ResultFormat] should be(emptyResult)
  }

  "Exporting relations" should "fail gracefully if no relations are defined" in {
    val request = ExportRequest("relations", start)
    exporter.executeExport(dataResolver, request.toJson).await(5).convertTo[ResultFormat] should be(emptyResult)
  }
}
