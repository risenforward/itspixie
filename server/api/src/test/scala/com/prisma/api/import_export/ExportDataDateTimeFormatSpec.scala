package com.prisma.api.import_export

import com.prisma.api.ApiBaseSpec
import com.prisma.api.database.DataResolver
import com.prisma.api.database.import_export.ImportExport.{Cursor, ExportRequest, ResultFormat}
import com.prisma.api.database.import_export.{BulkExport, BulkImport}
import com.prisma.shared.models.Project
import com.prisma.shared.project_dsl.SchemaDsl
import com.prisma.utils.await.AwaitUtils
import org.scalatest.{FlatSpec, Matchers}
import spray.json._
import com.prisma.api.database.import_export.ImportExport.MyJsonProtocol._

class ExportDataDateTimeFormatSpec extends FlatSpec with Matchers with ApiBaseSpec with AwaitUtils {

  "Exporting nodes" should "produce the correct ISO 8601 DateTime Format" in {
    val project: Project = SchemaDsl() { schema =>
      val model1 = schema
        .model("Model0")
        .field("a", _.String)
        .field("b", _.Int)
    }

    database.setup(project)
    database.truncate(project)
    val dataResolver: DataResolver = this.dataResolver(project)

    val nodes =
      """{ "valueType": "nodes", "values": [{"_typeName": "Model0", "id": "0","a": "test1", "b": 0, "createdAt": "2017-12-05T12:34:23.000Z", "updatedAt": "2017-12-05T12:34:23.000Z"}]}""".parseJson

    val importer = new BulkImport(project)
    val exporter = new BulkExport(project)
    importer.executeImport(nodes).await(5).toString should be("[]")

    val cursor     = Cursor(0, 0, 0, 0)
    val request    = ExportRequest("nodes", cursor)
    val firstChunk = exporter.executeExport(dataResolver, request.toJson).await(5).convertTo[ResultFormat]
    println(firstChunk)

    JsArray(firstChunk.out.jsonElements).toString should be(
      """[{"updatedAt":"2017-12-05T12:34:23.000Z","_typeName":"Model0","a":"test1","id":"0","b":0,"createdAt":"2017-12-05T12:34:23.000Z"}]""")
  }
}
