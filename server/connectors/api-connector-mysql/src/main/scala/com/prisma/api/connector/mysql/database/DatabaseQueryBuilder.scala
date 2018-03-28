package com.prisma.api.connector.mysql.database

import java.sql.{PreparedStatement, ResultSet}

import com.prisma.api.connector.Types.DataItemFilterCollection
import com.prisma.api.connector._
import com.prisma.api.connector.mysql.database.HelperTypes.ScalarListElement
import com.prisma.gc_values._
import com.prisma.shared.models.IdType.Id
import com.prisma.shared.models.{Function => _, _}
import slick.dbio.DBIOAction
import slick.dbio.Effect.Read
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.meta.{DatabaseMeta, MTable}
import slick.jdbc.{SQLActionBuilder, _}
import slick.sql.SqlStreamingAction
import scala.concurrent.ExecutionContext.Implicits.global

object DatabaseQueryBuilder {
  import JdbcExtensions._
  import QueryArgumentsExtensions._
  import SlickExtensions._

  def extractQueryArgs(projectId: String,
                       modelName: String,
                       args: Option[QueryArguments],
                       defaultOrderShortcut: Option[String],
                       overrideMaxNodeCount: Option[Int],
                       forList: Boolean = false): (Option[SQLActionBuilder], Option[SQLActionBuilder], Option[SQLActionBuilder]) = {
    args match {
      case None => (None, None, None)
      case Some(givenArgs: QueryArguments) =>
        val orderByCommand =
          if (forList) givenArgs.extractOrderByCommandForLists(projectId, modelName, defaultOrderShortcut)
          else givenArgs.extractOrderByCommand(projectId, modelName, defaultOrderShortcut)

        (
          givenArgs.extractWhereConditionCommand(projectId, modelName),
          orderByCommand,
          overrideMaxNodeCount match {
            case None                => givenArgs.extractLimitCommand(projectId, modelName)
            case Some(maxCount: Int) => givenArgs.extractLimitCommand(projectId, modelName, maxCount)
          }
        )
    }
  }

  def getResultForModel(model: Model): GetResult[PrismaNode] = GetResult { ps: PositionedResult =>
    getPrismaNode(model, ps)
  }

  private def getPrismaNode(model: Model, ps: PositionedResult) = {
    val data = model.scalarNonListFields.map(field => field.name -> ps.rs.getGcValue(field.name, field.typeIdentifier))

    PrismaNode(id = ps.rs.getId, data = RootGCValue(data: _*))
  }

  def getResultForModelAndRelationSide(model: Model, side: String): GetResult[PrismaNodeWithParent] = GetResult { ps: PositionedResult =>
    val node = getPrismaNode(model, ps)
    PrismaNodeWithParent(ps.rs.getParentId(side), node)
  }

  implicit object GetRelationNode extends GetResult[RelationNode] {
    override def apply(ps: PositionedResult): RelationNode = {
      val a = ps.rs.getString("A") //todo these are also IDS
      val b = ps.rs.getString("B")
      RelationNode(ps.rs.getId, IdGCValue(a), IdGCValue(b))
    }
  }

  implicit object GetRelationCount extends GetResult[(IdGCValue, Int)] {
    override def apply(ps: PositionedResult): (IdGCValue, Int) = (ps.rs.getId, ps.rs.getInt("Count"))
  }

  def getResultForScalarListField(field: Field): GetResult[ScalarListElement] = GetResult { ps: PositionedResult =>
    val resultSet = ps.rs
    val nodeId    = resultSet.getString("nodeId")
    val position  = resultSet.getInt("position")
    val value     = resultSet.getGcValue("value", field.typeIdentifier)
    ScalarListElement(nodeId, position, value)
  }

  def selectAllFromTableNew(
      projectId: String,
      model: Model,
      args: Option[QueryArguments],
      overrideMaxNodeCount: Option[Int] = None
  ): DBIOAction[ResolverResultNew[PrismaNode], NoStream, Effect] = {

    val tableName                                        = model.name
    val (conditionCommand, orderByCommand, limitCommand) = extractQueryArgs(projectId, tableName, args, None, overrideMaxNodeCount = overrideMaxNodeCount)

    val query = sql"select * from `#$projectId`.`#$tableName`" concat
      prefixIfNotNone("where", conditionCommand) concat
      prefixIfNotNone("order by", orderByCommand) concat
      prefixIfNotNone("limit", limitCommand)

    query.as[PrismaNode](getResultForModel(model)).map(args.get.resultTransform)
  }

  def selectAllFromRelationTable(
      projectId: String,
      relationId: String,
      args: Option[QueryArguments],
      overrideMaxNodeCount: Option[Int] = None
  ): DBIOAction[ResolverResultNew[RelationNode], NoStream, Effect] = {

    val tableName                                        = relationId
    val (conditionCommand, orderByCommand, limitCommand) = extractQueryArgs(projectId, tableName, args, None, overrideMaxNodeCount = overrideMaxNodeCount)

    val query = sql"select * from `#$projectId`.`#$tableName`" concat
      prefixIfNotNone("where", conditionCommand) concat
      prefixIfNotNone("order by", orderByCommand) concat
      prefixIfNotNone("limit", limitCommand)

    query.as[RelationNode].map(args.get.resultTransform)
  }

  def selectAllFromListTable(projectId: String,
                             model: Model,
                             field: Field,
                             args: Option[QueryArguments],
                             overrideMaxNodeCount: Option[Int] = None): DBIOAction[ResolverResultNew[ScalarListValues], NoStream, Effect] = {

    val tableName                                        = s"${model.name}_${field.name}"
    val (conditionCommand, orderByCommand, limitCommand) = extractQueryArgs(projectId, tableName, args, None, overrideMaxNodeCount = overrideMaxNodeCount, true)

    val query =
      sql"select * from `#$projectId`.`#$tableName`" concat
        prefixIfNotNone("where", conditionCommand) concat
        prefixIfNotNone("order by", orderByCommand) concat
        prefixIfNotNone("limit", limitCommand)

    query.as[ScalarListElement](getResultForScalarListField(field)).map { scalarListElements =>
      val res = args.get.resultTransform(scalarListElements)
      val convertedValues =
        res.nodes
          .groupBy(_.nodeId)
          .map { case (id, values) => ScalarListValues(IdGCValue(id), ListGCValue(values.sortBy(_.position).map(_.value))) }
          .toVector
      res.copy(nodes = convertedValues)
    }
  }

  def countAllFromModel(project: Project, model: Model, whereFilter: Option[DataItemFilterCollection]): DBIOAction[Int, NoStream, Effect] = {
    val whereSql = whereFilter.flatMap(where => QueryArgumentsHelpers.generateFilterConditions(project.id, model.name, where))
    val query    = sql"select count(*) from `#${project.id}`.`#${model.name}`" ++ prefixIfNotNone("where", whereSql)
    query.as[Int].map(_.head)
  }

  def batchSelectFromModelByUnique(projectId: String,
                                   model: Model,
                                   fieldName: String,
                                   values: Vector[GCValue]): SqlStreamingAction[Vector[PrismaNode], PrismaNode, Effect] = {
    val query = sql"select * from `#$projectId`.`#${model.name}` where `#$fieldName` in (" concat combineByComma(values.map(gcValueToSQLBuilder)) concat sql")"
    query.as[PrismaNode](getResultForModel(model))
  }

  def batchSelectFromModelByUniqueSimple(projectId: String, model: Model, fieldName: String, values: Vector[GCValue]): SimpleDBIO[Vector[PrismaNode]] =
    SimpleDBIO[Vector[PrismaNode]] { x =>
      val placeHolders                   = values.map(_ => "?").mkString(",")
      val query                          = s"select * from `$projectId`.`${model.name}` where `$fieldName` in ($placeHolders)"
      val batchSelect: PreparedStatement = x.connection.prepareStatement(query)
      values.zipWithIndex.foreach { gcValueWithIndex =>
        batchSelect.setGcValue(gcValueWithIndex._2 + 1, gcValueWithIndex._1)
      }
      val rs: ResultSet = batchSelect.executeQuery()

      var result: Vector[PrismaNode] = Vector.empty
      while (rs.next) {
        val data = model.scalarNonListFields.map(field => field.name -> rs.getGcValue(field.name, field.typeIdentifier))
        result = result :+ PrismaNode(id = rs.getId, data = RootGCValue(data: _*))
      }

      result
    }

  def selectFromScalarList(projectId: String,
                           modelName: String,
                           field: Field,
                           nodeIds: Vector[IdGCValue]): DBIOAction[Vector[ScalarListValues], NoStream, Effect] = {
    val query = sql"select nodeId, position, value from `#$projectId`.`#${modelName}_#${field.name}` where nodeId in (" concat combineByComma(
      nodeIds.map(gcValueToSQLBuilder)) concat sql")"

    query.as[ScalarListElement](getResultForScalarListField(field)).map { scalarListElements =>
      val grouped: Map[Id, Vector[ScalarListElement]] = scalarListElements.groupBy(_.nodeId)
      grouped.map {
        case (id, values) =>
          val gcValues = values.sortBy(_.position).map(_.value)
          ScalarListValues(IdGCValue(id), ListGCValue(gcValues))
      }.toVector
    }
  }

  def batchSelectAllFromRelatedModel(project: Project,
                                     fromField: Field,
                                     fromModelIds: Vector[IdGCValue],
                                     args: Option[QueryArguments]): DBIOAction[Vector[ResolverResultNew[PrismaNodeWithParent]], NoStream, Effect] = {

    val relatedModel      = fromField.relatedModel(project.schema).get
    val fieldTable        = fromField.relatedModel(project.schema).get.name
    val unsafeRelationId  = fromField.relation.get.id
    val modelRelationSide = fromField.relationSide.get.toString
    val fieldRelationSide = fromField.oppositeRelationSide.get.toString

    val (conditionCommand, orderByCommand, limitCommand) =
      extractQueryArgs(project.id, fieldTable, args, defaultOrderShortcut = Some(s"""`${project.id}`.`$unsafeRelationId`.$fieldRelationSide"""), None)

    def createQuery(id: String, modelRelationSide: String, fieldRelationSide: String) = {
      sql"""(select `#${project.id}`.`#$fieldTable`.*, `#${project.id}`.`#$unsafeRelationId`.A as __Relation__A,  `#${project.id}`.`#$unsafeRelationId`.B as __Relation__B
            from `#${project.id}`.`#$fieldTable`
           inner join `#${project.id}`.`#$unsafeRelationId`
           on `#${project.id}`.`#$fieldTable`.id = `#${project.id}`.`#$unsafeRelationId`.#$fieldRelationSide
           where `#${project.id}`.`#$unsafeRelationId`.#$modelRelationSide = '#$id' """ concat
        prefixIfNotNone("and", conditionCommand) concat
        prefixIfNotNone("order by", orderByCommand) concat
        prefixIfNotNone("limit", limitCommand) concat sql")"
    }

    // see https://github.com/graphcool/internal-docs/blob/master/relations.md#findings
    val resolveFromBothSidesAndMerge = fromField.relation.get.isSameFieldSameModelRelation(project.schema) && !fromField.isList

    val query = resolveFromBothSidesAndMerge match {
      case false =>
        fromModelIds.distinct.view.zipWithIndex.foldLeft(sql"")((a, b) =>
          a concat unionIfNotFirst(b._2) concat createQuery(b._1.value, modelRelationSide, fieldRelationSide))

      case true =>
        fromModelIds.distinct.view.zipWithIndex.foldLeft(sql"")(
          (a, b) =>
            a concat unionIfNotFirst(b._2) concat createQuery(b._1.value, modelRelationSide, fieldRelationSide) concat sql"union all " concat createQuery(
              b._1.value,
              fieldRelationSide,
              modelRelationSide))
    }

    query
      .as[PrismaNodeWithParent](getResultForModelAndRelationSide(relatedModel, fromField.relationSide.get.toString))
      .map { items =>
        val itemGroupsByModelId = items.groupBy(_.parentId)
        fromModelIds
          .map(id =>
            itemGroupsByModelId.find(_._1 == id) match {
              case Some((_, itemsForId)) => args.get.resultTransform(itemsForId).copy(parentModelId = Some(id))
              case None                  => ResolverResultNew(Vector.empty[PrismaNodeWithParent], hasPreviousPage = false, hasNextPage = false, parentModelId = Some(id))
          })
      }
  }

  def countAllFromRelatedModels(project: Project,
                                relationField: Field,
                                parentNodeIds: Vector[IdGCValue],
                                args: Option[QueryArguments]): SqlStreamingAction[Vector[(IdGCValue, Int)], (IdGCValue, Int), Effect] = {

    val fieldTable        = relationField.relatedModel(project.schema).get.name
    val unsafeRelationId  = relationField.relation.get.id
    val modelRelationSide = relationField.relationSide.get.toString
    val fieldRelationSide = relationField.oppositeRelationSide.get.toString

    val (conditionCommand, orderByCommand, limitCommand) =
      extractQueryArgs(project.id, fieldTable, args, defaultOrderShortcut = Some(s"""`${project.id}`.`$unsafeRelationId`.$fieldRelationSide"""), None)

    def createQuery(id: String) = {
      sql"""(select '#$id', count(*) from `#${project.id}`.`#$fieldTable`
           inner join `#${project.id}`.`#$unsafeRelationId`
           on `#${project.id}`.`#$fieldTable`.id = `#${project.id}`.`#$unsafeRelationId`.#$fieldRelationSide
           where `#${project.id}`.`#$unsafeRelationId`.#$modelRelationSide = '#$id' """ concat
        prefixIfNotNone("and", conditionCommand) concat
        prefixIfNotNone("order by", orderByCommand) concat
        prefixIfNotNone("limit", limitCommand) concat sql")"
    }

    val query = parentNodeIds.distinct.view.zipWithIndex.foldLeft(sql"")((a, b) => a concat unionIfNotFirst(b._2) concat createQuery(b._1.value))

    query.as[(IdGCValue, Int)]
  }

// used in tests only

  def getTables(projectId: String): DBIOAction[Vector[String], NoStream, Read] = {
    for {
      metaTables <- MTable.getTables(cat = Some(projectId), schemaPattern = None, namePattern = None, types = None)
    } yield metaTables.map(table => table.name.name)
  }

  def getSchemas: DBIOAction[Vector[String], NoStream, Read] = {
    for {
      catalogs <- DatabaseMeta.getCatalogs
    } yield catalogs
  }

  def unionIfNotFirst(index: Int): SQLActionBuilder = if (index == 0) sql"" else sql"union all "

  def itemCountForTable(projectId: String, modelName: String) = { // todo use count all from model
    sql"SELECT COUNT(*) AS Count FROM `#$projectId`.`#$modelName`"
  }

  def existsByModel(projectId: String, modelName: String): SQLActionBuilder = { //todo also replace in tests with count
    sql"select exists (select `id` from `#$projectId`.`#$modelName`)"
  }
}
