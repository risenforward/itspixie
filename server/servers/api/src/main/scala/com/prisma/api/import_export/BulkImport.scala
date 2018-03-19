package com.prisma.api.import_export

import com.prisma.api.ApiDependencies
import com.prisma.api.connector._
import com.prisma.api.import_export.ImportExport.MyJsonProtocol._
import com.prisma.api.import_export.ImportExport._
import com.prisma.shared.models._
import org.scalactic.{Bad, Good, Or}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Try}

class BulkImport(project: Project)(implicit apiDependencies: ApiDependencies) {
  import com.prisma.utils.future.FutureUtils._
  def executeImport(json: JsValue): Future[JsValue] = {

    val bundle = json.convertTo[ImportBundle]
    val count  = bundle.values.elements.length

    val mutactions: Vector[DatabaseMutaction Or Exception] =
      bundle.valueType match {
        case "nodes"     => generateImportNodesDBActions(bundle.values.elements.map(convertToImportNode))
        case "relations" => generateImportRelationsDBActions(bundle.values.elements.map(convertToImportRelation)).map(Good(_))
        case "lists"     => generateImportListsDBActions(bundle.values.elements.map(convertToImportList)).map(Good(_))

      }

    val res = Future.sequence(
      mutactions.map {
        case Good(m)        => apiDependencies.databaseMutactionExecutor.execute(Vector(m)).toFutureTry
        case Bad(exception) => Future.successful(Failure(exception))
      }
    )

    def messageWithOutConnection(tryelem: Try[Any]): String = tryelem.failed.get.getMessage.substring(tryelem.failed.get.getMessage.indexOf(")") + 1)

    res
      .map(vector =>
        vector.zipWithIndex.collect {
          case (elem, idx) if elem.isFailure && idx < count  => Map("index" -> idx, "message"           -> messageWithOutConnection(elem)).toJson
          case (elem, idx) if elem.isFailure && idx >= count => Map("index" -> (idx - count), "message" -> messageWithOutConnection(elem)).toJson
      })
      .map(x => JsArray(x))
  }

  private def getImportIdentifier(map: Map[String, Any]): ImportIdentifier =
    ImportIdentifier(map("_typeName").asInstanceOf[String], map("id").asInstanceOf[String])

  private def convertToImportNode(json: JsValue): ImportNode = {
    val map      = json.convertTo[Map[String, Any]]
    val valueMap = map.collect { case (k, v) if k != "_typeName" && k != "id" => (k, v) }

    ImportNode(getImportIdentifier(map), valueMap)
  }

  private def convertToImportList(json: JsValue): ImportList = {
    val map      = json.convertTo[Map[String, Any]]
    val valueMap = map.collect { case (k, v) if k != "_typeName" && k != "id" => (k, v.asInstanceOf[List[Any]].toVector) }

    ImportList(getImportIdentifier(map), valueMap)
  }

  private def convertToImportRelation(json: JsValue): ImportRelation = {
    val array    = json.convertTo[JsArray]
    val leftMap  = array.elements.head.convertTo[Map[String, Option[String]]]
    val rightMap = array.elements.last.convertTo[Map[String, Option[String]]]
    val left     = ImportRelationSide(ImportIdentifier(leftMap("_typeName").get, leftMap("id").get), leftMap.get("fieldName").flatten)
    val right    = ImportRelationSide(ImportIdentifier(rightMap("_typeName").get, rightMap("id").get), rightMap.get("fieldName").flatten)

    ImportRelation(left, right)
  }

  private def dateTimeFromISO8601(v: Any) = {
    val string = v.asInstanceOf[String]
    //"2017-12-05T12:34:23.000Z" to "2017-12-05 12:34:23.000 " which MySQL will accept
    string.replace("T", " ").replace("Z", " ")
  }

  private def generateImportNodesDBActions(nodes: Vector[ImportNode]): Vector[CreateDataItemsImport Or Exception] = {
    val createDataItems: Vector[Or[CreateDataItemImport, Exception]] = nodes.map { element =>
      val id    = element.identifier.id
      val model = project.schema.getModelByName_!(element.identifier.typeName)

      val elementReferenceToNonExistentField = element.values.keys.find(key => model.getFieldByName(key).isEmpty)

      elementReferenceToNonExistentField match {
        case Some(key) =>
          Bad(new Exception(s"Unknown field '$key' in field list"))

        case None =>
          val formattedValues = element.values.collect {
            case (k, v) if k == "createdAt" || k == "updatedAt"                                => (k, dateTimeFromISO8601(v))
            case (k, v) if model.getFieldByName_!(k).typeIdentifier == TypeIdentifier.DateTime => (k, dateTimeFromISO8601(v))
            case (k, v) if model.getFieldByName_!(k).typeIdentifier == TypeIdentifier.Json     => (k, v.toJson)
            case (k, v)                                                                        => (k, v)
          }
          val values = CoolArgs(formattedValues + ("id" -> id))
          Good(CreateDataItemImport(project, model, values))
      }
    }

    val exceptions: Vector[Bad[Exception]] = createDataItems.collect { case x: Bad[Exception] => x }

    if (exceptions.nonEmpty) {
      exceptions
    } else {
      val creates                                                = createDataItems.collect { case x: Good[CreateDataItemImport] => x.get }
      val groupedItems: Map[Model, Vector[CreateDataItemImport]] = creates.groupBy(_.model)
      groupedItems.map { case (model, group) => Good(CreateDataItemsImport(project, model, group.map(_.args))) }.toVector
    }
  }

  private def generateImportRelationsDBActions(relations: Vector[ImportRelation]): Vector[CreateRelationRowsImport] = {
    val createRows = relations.map { element =>
      val (left, right) = (element.left, element.right) match {
        case (l, r) if l.fieldName.isDefined => (l, r)
        case (l, r) if r.fieldName.isDefined => (r, l)
        case _                               => throw sys.error("Invalid ImportRelation at least one fieldName needs to be defined.")
      }
      val fromModel                                                 = project.schema.getModelByName_!(left.identifier.typeName)
      val fromField                                                 = fromModel.getFieldByName_!(left.fieldName.get)
      val relationSide: com.prisma.shared.models.RelationSide.Value = fromField.relationSide.get
      val relation: Relation                                        = fromField.relation.get
      val aValue: String                                            = if (relationSide == RelationSide.A) left.identifier.id else right.identifier.id
      val bValue: String                                            = if (relationSide == RelationSide.A) right.identifier.id else left.identifier.id
      CreateRelationRow(project, relation, aValue, bValue)
    }
    val groupedItems = createRows.groupBy(_.relation)
    groupedItems.map { case (relation, group) => CreateRelationRowsImport(project, relation, group.map(item => (item.a, item.b))) }.toVector
  }

  private def generateImportListsDBActions(lists: Vector[ImportList]): Vector[PushScalarListsImport] = {
    val listsCreate = lists.flatMap { element =>
      val model                         = project.schema.getModelByName_!(element.identifier.typeName)
      def isDateTime(fieldName: String) = model.getFieldByName_!(fieldName).typeIdentifier == TypeIdentifier.DateTime
      def isJson(fieldName: String)     = model.getFieldByName_!(fieldName).typeIdentifier == TypeIdentifier.Json

      element.values.map {
        case (fieldName, values) if isDateTime(fieldName) =>
          PushScalarListImport(project, s"${model.name}_{$fieldName}", element.identifier.id, values.map(dateTimeFromISO8601))
        case (fieldName, values) if isJson(fieldName) =>
          PushScalarListImport(project, s"${model.name}_{$fieldName}", element.identifier.id, values.map(v => v.toJson))
        case (fieldName, values) =>
          PushScalarListImport(project, s"${model.name}_{$fieldName}", element.identifier.id, values)
      }
    }

    val groupedItems = listsCreate.groupBy(_.tableName)
    groupedItems.map { case (tableName, group) => PushScalarListsImport(project, tableName, group.map(item => (item.id, item.values))) }.toVector
  }
}
