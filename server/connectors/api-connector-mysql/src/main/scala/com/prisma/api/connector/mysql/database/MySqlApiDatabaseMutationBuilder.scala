package com.prisma.api.connector.mysql.database

import java.sql.{PreparedStatement, Statement}
import java.util.Date
import com.prisma.api.connector._
import com.prisma.api.connector.mysql.database.JdbcExtensions._
import com.prisma.api.connector.mysql.database.MySqlSlickExtensions._
import com.prisma.api.schema.UserFacingError
import com.prisma.gc_values._
import com.prisma.shared.models._
import cool.graph.cuid.Cuid
import org.joda.time.{DateTime, DateTimeZone}
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.SQLActionBuilder
import slick.sql.{SqlAction, SqlStreamingAction}

object MySqlApiDatabaseMutationBuilder {
  // region CREATE

  def createDataItem(projectId: String, path: Path, args: PrismaArgs) = {

    SimpleDBIO[Unit] { x =>
      val columns      = path.lastModel.scalarNonListFields.map(_.name)
      val escapedKeys  = columns.map(column => s"`$column`").mkString(",")
      val placeHolders = columns.map(_ => "?").mkString(",")

      val query                         = s"INSERT INTO `$projectId`.`${path.lastModel.name}` ($escapedKeys) VALUES ($placeHolders)"
      val itemInsert: PreparedStatement = x.connection.prepareStatement(query)

      columns.zipWithIndex.foreach {
        case (column, index) =>
          args.raw.asRoot.map.get(column) match {
            case Some(NullGCValue) if column == "createdAt" || column == "updatedAt" => itemInsert.setTimestamp(index + 1, currentTimeStampUTC)
            case Some(gCValue)                                                       => itemInsert.setGcValue(index + 1, gCValue)
            case None if column == "createdAt" || column == "updatedAt"              => itemInsert.setTimestamp(index + 1, currentTimeStampUTC)
            case None                                                                => itemInsert.setNull(index + 1, java.sql.Types.NULL)
          }
      }
      itemInsert.execute()
    }
  }

  def createRelayRow(projectId: String, path: Path): SqlStreamingAction[Vector[Int], Int, Effect]#ResultAction[Int, NoStream, Effect] = {
    val where = path.lastCreateWhere_!
    (sql"INSERT INTO `#$projectId`.`_RelayId` (`id`, `stableModelIdentifier`) VALUES (${where.fieldGCValue}, ${where.model.stableIdentifier})").asUpdate
  }

  def createRelationRowByPath(projectId: String, path: Path): SqlAction[Int, NoStream, Effect] = {
    val childWhere = path.lastEdge_! match {
      case _: ModelEdge   => sys.error("Needs to be a node edge.")
      case edge: NodeEdge => edge.childWhere
    }
    val relationId = Cuid.createCuid()
    (sql"insert into `#$projectId`.`#${path.lastRelation_!.relationTableName}` (`id`, `#${path.parentSideOfLastEdge}`, `#${path.childSideOfLastEdge}`)" ++
      sql"Select '#$relationId'," ++ pathQueryForLastChild(projectId, path.removeLastEdge) ++ sql"," ++
      sql"`id` FROM `#$projectId`.`#${childWhere.model.name}` where `#${childWhere.field.name}` = ${childWhere.fieldGCValue}" ++
      sql"on duplicate key update `#$projectId`.`#${path.lastRelation_!.relationTableName}`.id = `#$projectId`.`#${path.lastRelation_!.relationTableName}`.id").asUpdate
  }

  //endregion

  //region UPDATE

  def updateDataItems(projectId: String, model: Model, args: PrismaArgs, whereFilter: Option[Filter]) = {
    val updateValues = combineByComma(args.raw.asRoot.map.map { case (k, v) => escapeKey(k) ++ sql" = $v" })

    if (updateValues.isDefined) {
      (sql"UPDATE `#${projectId}`.`#${model.name}`" ++ sql"SET " ++ addUpdatedDateTime(model, updateValues) ++ whereFilterAppendix(projectId,
                                                                                                                                   model.name,
                                                                                                                                   whereFilter)).asUpdate
    } else {
      DBIOAction.successful(())
    }
  }

  def updateDataItemByPath(projectId: String, path: Path, updateArgs: PrismaArgs) = {
    val updateValues = combineByComma(updateArgs.raw.asRoot.map.map { case (k, v) => escapeKey(k) ++ sql" = $v" })
    def fromEdge(edge: Edge) = edge match {
      case edge: NodeEdge => sql" `#${path.childSideOfLastEdge}`" ++ idFromWhereEquals(projectId, edge.childWhere) ++ sql" AND "
      case _: ModelEdge   => sql""
    }

    val baseQuery = sql"UPDATE `#${projectId}`.`#${path.lastModel.name}` SET " ++ addUpdatedDateTime(path.lastModel, updateValues) ++ sql"WHERE `id` ="

    if (updateArgs.raw.asRoot.map.isEmpty) {
      DBIOAction.successful(())
    } else {
      val query = path.lastEdge match {
        case Some(edge) =>
          baseQuery ++ sql"(SELECT `#${path.childSideOfLastEdge}` " ++
            sql"FROM `#${projectId}`.`#${path.lastRelation_!.relationTableName}`" ++
            sql"WHERE" ++ fromEdge(edge) ++ sql"`#${path.parentSideOfLastEdge}` = " ++ pathQueryForLastParent(projectId, path) ++ sql")"
        case None => baseQuery ++ idFromWhere(projectId, path.root)
      }
      query.asUpdate
    }
  }

  //endregion

  private def addUpdatedDateTime(model: Model, updateValues: Option[SQLActionBuilder]): Option[SQLActionBuilder] = {
    model.updatedAtField match {
      case Some(updatedAtField) =>
        val today              = new Date()
        val exactlyNow         = new DateTime(today).withZone(DateTimeZone.UTC)
        val currentDateGCValue = DateTimeGCValue(exactlyNow)
        val updatedAt          = sql"""`#${updatedAtField.dbName}` = $currentDateGCValue """
        combineByComma(updateValues ++ List(updatedAt))

      case None =>
        updateValues
    }
  }

  //region UPSERT

  def upsert(projectId: String,
             createPath: Path,
             updatePath: Path,
             createArgs: PrismaArgs,
             updateArgs: PrismaArgs,
             create: DBIOAction[Any, NoStream, Effect],
             update: DBIOAction[Any, NoStream, Effect],
             createNested: Vector[DBIOAction[Any, NoStream, Effect.All]],
             updateNested: Vector[DBIOAction[Any, NoStream, Effect.All]]) = {

    val query = sql"select exists ( SELECT `id` FROM `#$projectId`.`#${updatePath.lastModel.name}` WHERE `id` = " ++ pathQueryForLastChild(projectId,
                                                                                                                                           updatePath) ++ sql")"
    val condition = query.as[Boolean]
    // insert creates item first, then the list values
    val allCreateActions = Vector(createDataItem(projectId, createPath, createArgs), createRelayRow(projectId, createPath), create) ++ createNested
    val qCreate          = DBIOAction.seq(allCreateActions: _*)
    // update first sets the lists, then updates the item
    val allUpdateActions = update +: updateNested :+ updateDataItemByPath(projectId, updatePath, updateArgs)
    val qUpdate          = DBIOAction.seq(allUpdateActions: _*)

    ifThenElse(condition, qUpdate, qCreate)
  }

  def upsertIfInRelationWith(project: Project,
                             createPath: Path,
                             updatePath: Path,
                             createArgs: PrismaArgs,
                             updateArgs: PrismaArgs,
                             scalarListCreate: DBIOAction[Any, NoStream, Effect],
                             scalarListUpdate: DBIOAction[Any, NoStream, Effect],
                             createCheck: DBIOAction[Any, NoStream, Effect],
                             createNested: Vector[DBIOAction[Any, NoStream, Effect.All]],
                             updateNested: Vector[DBIOAction[Any, NoStream, Effect.All]]) = {

    def existsNodeIsInRelationshipWith = {
      def nodeSelector(last: Edge) = last match {
        case edge: NodeEdge => sql" `id`" ++ idFromWhereEquals(project.id, edge.childWhere) ++ sql" AND "
        case _: ModelEdge   => sql""
      }

      sql"""select EXISTS (
            select `id`from `#${project.id}`.`#${updatePath.lastModel.name}`
            where""" ++ nodeSelector(updatePath.lastEdge_!) ++
        sql""" `id` IN""" ++ MySqlApiDatabaseMutationBuilder.pathQueryThatUsesWholePath(project.id, updatePath) ++ sql")"
    }

    val condition        = existsNodeIsInRelationshipWith.as[Boolean]
    val allCreateActions = Vector(createDataItem(project.id, createPath, createArgs), createRelayRow(project.id, createPath), createCheck, scalarListCreate) ++ createNested
    val qCreate          = DBIOAction.seq(allCreateActions: _*)
    //update updates list values first and then the item
    val allUpdateActions = scalarListUpdate +: updateNested :+ updateDataItemByPath(project.id, updatePath, updateArgs)
    val qUpdate          = DBIOAction.seq(allUpdateActions: _*)

    ifThenElseNestedUpsert(condition, qUpdate, qCreate)
  }

  //endregion

  //region DELETE

  def deleteDataItems(project: Project, model: Model, whereFilter: Option[Filter]) = {
    (sql"DELETE FROM `#${project.id}`.`#${model.name}`" ++ whereFilterAppendix(project.id, model.name, whereFilter)).asUpdate
  }

  def deleteRelayIds(project: Project, model: Model, whereFilter: Option[Filter]) = {
    (sql"DELETE FROM `#${project.id}`.`_RelayId`" ++
      (sql"WHERE `id` IN (" ++
        sql"SELECT `id`" ++
        sql"FROM `#${project.id}`.`#${model.name}`" ++ whereFilterAppendix(project.id, model.name, whereFilter) ++ sql")")).asUpdate
  }

  def deleteDataItem(projectId: String, path: Path) =
    (sql"DELETE FROM `#$projectId`.`#${path.lastModel.name}` WHERE `id` = " ++ pathQueryForLastChild(projectId, path)).asUpdate

  def deleteRelayRow(projectId: String, path: Path) =
    (sql"DELETE FROM `#$projectId`.`_RelayId` WHERE `id` =" ++ pathQueryForLastChild(projectId, path)).asUpdate

  def deleteRelationRowByParent(projectId: String, path: Path) = {
    (sql"DELETE FROM `#$projectId`.`#${path.lastRelation_!.relationTableName}` WHERE `#${path.parentSideOfLastEdge}` = " ++ pathQueryForLastParent(
      projectId,
      path)).asUpdate
  }

  def deleteRelationRowByChildWithWhere(projectId: String, path: Path) = {
    val where = path.lastEdge_! match {
      case _: ModelEdge   => sys.error("Should be a node Edge")
      case edge: NodeEdge => edge.childWhere

    }
    (sql"DELETE FROM `#$projectId`.`#${path.lastRelation_!.relationTableName}` WHERE `#${path.childSideOfLastEdge}`" ++ idFromWhereEquals(projectId, where)).asUpdate
  }

  def deleteRelationRowByParentAndChild(projectId: String, path: Path) = {
    (sql"DELETE FROM `#$projectId`.`#${path.lastRelation_!.relationTableName}` " ++
      sql"WHERE `#${path.childSideOfLastEdge}` = " ++ pathQueryForLastChild(projectId, path) ++
      sql" AND `#${path.parentSideOfLastEdge}` = " ++ pathQueryForLastParent(projectId, path)).asUpdate
  }

  def cascadingDeleteChildActions(projectId: String, path: Path) = {
    val deleteRelayIds = (sql"DELETE FROM `#$projectId`.`_RelayId` WHERE `id` IN (" ++ pathQueryForLastChild(projectId, path) ++ sql")").asUpdate
    val deleteDataItems =
      (sql"DELETE FROM `#$projectId`.`#${path.lastModel.name}` WHERE `id` IN (" ++ pathQueryForLastChild(projectId, path) ++ sql")").asUpdate
    DBIO.seq(deleteRelayIds, deleteDataItems)
  }

  //endregion

  //region SCALAR LISTS
  def getDbActionForScalarLists(project: Project, path: Path, args: Vector[(String, ListGCValue)]) = {
    if (args.isEmpty) {
      DBIOAction.successful(())
    } else {
      val actions = args.map {
        case (fieldName, listGCValue) =>
          listGCValue.isEmpty match {
            case true  => setScalarListToEmpty(project.id, path, fieldName)
            case false => setScalarList(project.id, path, fieldName, listGCValue)
          }
      }
      DBIOAction.seq(actions: _*)
    }
  }

  def setScalarList(projectId: String, path: Path, fieldName: String, listGCValue: ListGCValue) = {
    val escapedValueTuples = for {
      (value, position) <- listGCValue.values.zip((1 to listGCValue.size).map(_ * 1000))
    } yield {
      sql"(@nodeId, $position, $value)"
    }

    DBIO.seq(
      (sql"set @nodeId := " ++ pathQueryForLastChild(projectId, path)).asUpdate,
      sqlu"""delete from `#$projectId`.`#${path.lastModel.name}_#${fieldName}` where nodeId = @nodeId""",
      (sql"insert into `#$projectId`.`#${path.lastModel.name}_#${fieldName}` (`nodeId`, `position`, `value`) values " concat combineByComma(escapedValueTuples)).asUpdate
    )
  }

  def setScalarListToEmpty(projectId: String, path: Path, fieldName: String) = {
    (sql"DELETE FROM `#$projectId`.`#${path.lastModel.name}_#${fieldName}` WHERE `nodeId` = " ++ pathQueryForLastChild(projectId, path)).asUpdate
  }

  def setManyScalarLists(projectId: String, model: Model, listFieldMap: Vector[(String, ListGCValue)], whereFilter: Option[Filter]) = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val idQuery: SqlStreamingAction[Vector[String], String, Effect] =
      (sql"SELECT `id` FROM `#${projectId}`.`#${model.name}`" ++ whereFilterAppendix(projectId, model.name, whereFilter)).as[String]

    def listInsert(ids: Vector[String]) = {
      if (ids.isEmpty) {
        DBIOAction.successful(())
      } else {

        SimpleDBIO[Unit] { x =>
          def valueTuplesForListField(listGCValue: ListGCValue) =
            for {
              nodeId                   <- ids
              (escapedValue, position) <- listGCValue.values.zip((1 to listGCValue.size).map(_ * 1000))
            } yield {
              (nodeId, position, escapedValue)
            }

          val combinedNodeIdsString = ids.map(id => s"'$id'") mkString ("(", ",", ")")

          listFieldMap.foreach {
            case (fieldName, listGCValue) =>
              val wipe                             = s"DELETE  FROM `$projectId`.`${model.name}_$fieldName` WHERE `nodeId` IN $combinedNodeIdsString"
              val wipeOldValues: PreparedStatement = x.connection.prepareStatement(wipe)
              wipeOldValues.executeUpdate()

              val insert                             = s"INSERT INTO `$projectId`.`${model.name}_$fieldName` (`nodeId`, `position`, `value`) VALUES (?,?,?)"
              val insertNewValues: PreparedStatement = x.connection.prepareStatement(insert)
              val newValueTuples                     = valueTuplesForListField(listGCValue)
              newValueTuples.foreach { tuple =>
                insertNewValues.setString(1, tuple._1)
                insertNewValues.setInt(2, tuple._2)
                insertNewValues.setGcValue(3, tuple._3)
                insertNewValues.addBatch()
              }
              insertNewValues.executeBatch()
          }
        }
      }
    }

    for {
      nodeIds <- idQuery
      action  <- listInsert(nodeIds)
    } yield action
  }

  //endregion

  //region RESET DATA
  def disableForeignKeyConstraintChecks               = sqlu"SET FOREIGN_KEY_CHECKS=0"
  def resetData(projectId: String, tableName: String) = sqlu"TRUNCATE TABLE `#$projectId`.`#$tableName`"
  def enableForeignKeyConstraintChecks                = sqlu"SET FOREIGN_KEY_CHECKS=1"

  //endregion

  // region HELPERS

  def idFromWhere(projectId: String, where: NodeSelector): SQLActionBuilder = (where.isId, where.fieldGCValue) match {
    case (true, NullGCValue)  => sys.error("id should not be NULL")
    case (true, idValue)      => sql"$idValue"
    case (false, NullGCValue) => sql"(SELECT `id` FROM (SELECT * FROM `#$projectId`.`#${where.model.name}`) IDFROMWHERE WHERE `#${where.field.name}` is NULL)"
    case (false, value)       => sql"(SELECT `id` FROM (SELECT * FROM `#$projectId`.`#${where.model.name}`) IDFROMWHERE WHERE `#${where.field.name}` = $value)"
  }

  def idFromWhereEquals(projectId: String, where: NodeSelector): SQLActionBuilder = sql" = " ++ idFromWhere(projectId, where)

  def idFromWherePath(projectId: String, where: NodeSelector): SQLActionBuilder = {
    sql"(SELECT `id` FROM (SELECT  * From `#$projectId`.`#${where.model.name}`) IDFROMWHEREPATH WHERE `#${where.field.name}` = ${where.fieldGCValue})"
  }

  //we could probably save even more joins if we start the paths always at the last node edge

  def pathQueryForLastParent(projectId: String, path: Path): SQLActionBuilder = pathQueryForLastChild(projectId, path.removeLastEdge)

  def pathQueryForLastChild(projectId: String, path: Path): SQLActionBuilder = {
    path.edges match {
      case Nil                                => idFromWhere(projectId, path.root)
      case x if x.last.isInstanceOf[NodeEdge] => idFromWhere(projectId, x.last.asInstanceOf[NodeEdge].childWhere)
      case _                                  => pathQueryThatUsesWholePath(projectId, path)
    }
  }

  object ::> { def unapply[A](l: List[A]) = Some((l.init, l.last)) }

  def pathQueryThatUsesWholePath(projectId: String, path: Path): SQLActionBuilder = {
    path.edges match {
      case Nil =>
        idFromWherePath(projectId, path.root)

      case _ ::> last =>
        val childWhere = last match {
          case edge: NodeEdge => sql" `#${edge.childRelationSide}`" ++ idFromWhereEquals(projectId, edge.childWhere) ++ sql" AND "
          case _: ModelEdge   => sql""
        }

        sql"(SELECT `#${last.childRelationSide}`" ++
          sql" FROM (SELECT * FROM `#$projectId`.`#${last.relation.relationTableName}`) PATHQUERY" ++
          sql" WHERE " ++ childWhere ++ sql"`#${last.parentRelationSide}` IN (" ++ pathQueryForLastParent(projectId, path) ++ sql"))"
    }
  }

  def whereFailureTrigger(project: Project, where: NodeSelector) = {
    val table = where.model.name
    val query = sql"(SELECT `id` FROM `#${project.id}`.`#${where.model.name}` WHEREFAILURETRIGGER WHERE `#${where.field.name}` = ${where.fieldGCValue})"

    triggerFailureWhenNotExists(project, query, table)
  }

  def connectionFailureTrigger(project: Project, path: Path) = {
    val table = path.lastRelation.get.relationTableName

    val lastChildWhere = path.lastEdge_! match {
      case edge: NodeEdge => sql" `#${path.childSideOfLastEdge}`" ++ idFromWhereEquals(project.id, edge.childWhere) ++ sql" AND "
      case _: ModelEdge   => sql""
    }

    val query =
      sql"SELECT `id` FROM `#${project.id}`.`#$table` CONNECTIONFAILURETRIGGERPATH" ++
        sql"WHERE" ++ lastChildWhere ++ sql"`#${path.parentSideOfLastEdge}` = " ++ pathQueryForLastParent(project.id, path)

    triggerFailureWhenNotExists(project, query, table)
  }

  def oldParentFailureTriggerForRequiredRelations(project: Project, relation: Relation, where: NodeSelector, childSide: RelationSide.Value) = {
    val table = relation.relationTableName
    val query = sql"SELECT `id` FROM `#${project.id}`.`#$table` OLDPARENTFAILURETRIGGER WHERE `#$childSide` " ++ idFromWhereEquals(project.id, where)

    triggerFailureWhenExists(project, query, table)
  }

  def oldParentFailureTrigger(project: Project, path: Path) = {
    val table = path.lastRelation_!.relationTableName
    val query = sql"SELECT `id` FROM `#${project.id}`.`#$table` OLDPARENTPATHFAILURETRIGGER WHERE `#${path.childSideOfLastEdge}` IN (" ++ pathQueryForLastChild(
      project.id,
      path) ++ sql")"
    triggerFailureWhenExists(project, query, table)
  }

  def oldParentFailureTriggerByField(project: Project, path: Path, field: RelationField) = {
    val table = field.relation.relationTableName
    val query = sql"SELECT `id` FROM `#${project.id}`.`#$table` OLDPARENTPATHFAILURETRIGGERBYFIELD WHERE `#${field.oppositeRelationSide}` IN (" ++ pathQueryForLastChild(
      project.id,
      path) ++ sql")"
    triggerFailureWhenExists(project, query, table)
  }

  def oldParentFailureTriggerByFieldAndFilter(project: Project, model: Model, whereFilter: Option[Filter], field: RelationField) = {
    val table = field.relation.relationTableName

    val query = sql"SELECT `id` FROM `#${project.id}`.`#$table` OLDPARENTPATHFAILURETRIGGERBYFIELDANDFILTER" ++
      sql"WHERE `#${field.oppositeRelationSide}` IN (SELECT `id` FROM `#${project.id}`.`#${model.name}` " ++
      whereFilterAppendix(project.id, model.name, whereFilter) ++ sql")"
    triggerFailureWhenExists(project, query, table)
  }

  def oldChildFailureTrigger(project: Project, path: Path) = {
    val table = path.lastRelation_!.relationTableName
    val query = sql"SELECT `id` FROM `#${project.id}`.`#$table` OLDCHILDPATHFAILURETRIGGER WHERE `#${path.parentSideOfLastEdge}` IN (" ++ pathQueryForLastParent(
      project.id,
      path) ++ sql")"
    triggerFailureWhenExists(project, query, table)
  }

  def ifThenElse(condition: SqlStreamingAction[Vector[Boolean], Boolean, Effect],
                 thenMutactions: DBIOAction[Unit, NoStream, Effect.All],
                 elseMutactions: DBIOAction[Unit, NoStream, Effect.All]) = {
    import scala.concurrent.ExecutionContext.Implicits.global
    for {
      exists <- condition
      action <- if (exists.head) thenMutactions else elseMutactions
    } yield action
  }

  def ifThenElseNestedUpsert(condition: SqlStreamingAction[Vector[Boolean], Boolean, Effect],
                             thenMutactions: DBIOAction[Unit, NoStream, Effect.All],
                             elseMutactions: DBIOAction[Unit, NoStream, Effect.All]) = {
    import scala.concurrent.ExecutionContext.Implicits.global
    for {
      exists <- condition
      action <- if (exists.head) thenMutactions else elseMutactions
    } yield action
  }

  def ifThenElseError(condition: SqlStreamingAction[Vector[Boolean], Boolean, Effect],
                      thenMutactions: DBIOAction[Unit, NoStream, Effect],
                      elseError: UserFacingError) = {
    import scala.concurrent.ExecutionContext.Implicits.global
    for {
      exists <- condition
      action <- if (exists.head) thenMutactions else throw elseError
    } yield action
  }
  def triggerFailureWhenExists(project: Project, query: SQLActionBuilder, table: String)    = triggerFailureInternal(project, query, table, notExists = false)
  def triggerFailureWhenNotExists(project: Project, query: SQLActionBuilder, table: String) = triggerFailureInternal(project, query, table, notExists = true)

  private def triggerFailureInternal(project: Project, query: SQLActionBuilder, table: String, notExists: Boolean) = {
    val notValue = if (notExists) sql"" else sql"not"

    (sql"select case" ++
      sql"when" ++ notValue ++ sql"exists( " ++ query ++ sql" )" ++
      sql"then 1" ++
      sql"else (select COLUMN_NAME" ++
      sql"from information_schema.columns" ++
      sql"where table_schema = ${project.id} AND TABLE_NAME = $table)end;").as[Int]
  }

  //endregion

  def createDataItemsImport(mutaction: CreateDataItemsImport): SimpleDBIO[Vector[String]] = {

    SimpleDBIO[Vector[String]] { x =>
      val model         = mutaction.model
      val argsWithIndex = mutaction.args.zipWithIndex

      val nodeResult: Vector[String] = try {
        val columns      = model.scalarNonListFields.map(_.name)
        val escapedKeys  = columns.map(column => s"`$column`").mkString(",")
        val placeHolders = columns.map(_ => "?").mkString(",")

        val query                         = s"INSERT INTO `${mutaction.project.id}`.`${model.name}` ($escapedKeys) VALUES ($placeHolders)"
        val itemInsert: PreparedStatement = x.connection.prepareStatement(query)
        val currentTimeStamp              = currentTimeStampUTC

        mutaction.args.foreach { arg =>
          columns.zipWithIndex.foreach { columnAndIndex =>
            val index  = columnAndIndex._2 + 1
            val column = columnAndIndex._1

            arg.raw.asRoot.map.get(column) match {
              case Some(x)                                                => itemInsert.setGcValue(index, x)
              case None if column == "createdAt" || column == "updatedAt" => itemInsert.setTimestamp(index, currentTimeStamp)
              case None                                                   => itemInsert.setNull(index, java.sql.Types.NULL)
            }
          }
          itemInsert.addBatch()
        }

        itemInsert.executeBatch()

        Vector.empty
      } catch {
        case e: java.sql.BatchUpdateException =>
          e.getUpdateCounts.zipWithIndex
            .filter(element => element._1 == Statement.EXECUTE_FAILED)
            .map { failed =>
              val failedId = argsWithIndex.find(_._2 == failed._2).get._1.raw.asRoot.idField.value
              s"Failure inserting ${model.name} with Id: $failedId. Cause: ${removeConnectionInfoFromCause(e.getCause.toString)}"
            }
            .toVector
        case e: Exception => Vector(e.getCause.toString)
      }

      val relayResult: Vector[String] = try {
        val relayQuery                     = s"INSERT INTO `${mutaction.project.id}`.`_RelayId` (`id`, `stableModelIdentifier`) VALUES (?,?)"
        val relayInsert: PreparedStatement = x.connection.prepareStatement(relayQuery)

        mutaction.args.foreach { arg =>
          relayInsert.setString(1, arg.raw.asRoot.idField.value)
          relayInsert.setString(2, model.stableIdentifier)
          relayInsert.addBatch()
        }
        relayInsert.executeBatch()

        Vector.empty
      } catch {
        case e: java.sql.BatchUpdateException =>
          e.getUpdateCounts.zipWithIndex
            .filter(element => element._1 == Statement.EXECUTE_FAILED)
            .map { failed =>
              val failedId = argsWithIndex.find(_._2 == failed._2).get._1.raw.asRoot.idField.value
              s"Failure inserting RelayRow with Id: $failedId. Cause: ${removeConnectionInfoFromCause(e.getCause.toString)}"
            }
            .toVector
        case e: Exception => Vector(e.getCause.toString)
      }

      val res = nodeResult ++ relayResult
      if (res.nonEmpty) throw new Exception(res.mkString("-@-"))
      res
    }
  }

  def removeConnectionInfoFromCause(cause: String): String = {
    val connectionSubStringStart = cause.indexOf("(conn=")
    val firstPart                = cause.substring(0, connectionSubStringStart)
    val temp                     = cause.substring(connectionSubStringStart + 6)
    val endOfConnectionSubstring = temp.indexOf(")") + 2
    val secondPart               = temp.substring(endOfConnectionSubstring)

    firstPart + secondPart
  }

  def createRelationRowsImport(mutaction: CreateRelationRowsImport): SimpleDBIO[Vector[String]] = {
    val argsWithIndex: Seq[((String, String), Int)] = mutaction.args.zipWithIndex

    SimpleDBIO[Vector[String]] { x =>
      val res = try {
        val query                             = s"INSERT INTO `${mutaction.project.id}`.`${mutaction.relation.relationTableName}` (`id`, `a`, `b`) VALUES (?,?,?)"
        val relationInsert: PreparedStatement = x.connection.prepareStatement(query)
        mutaction.args.foreach { arg =>
          relationInsert.setString(1, Cuid.createCuid())
          relationInsert.setString(2, arg._1)
          relationInsert.setString(3, arg._2)
          relationInsert.addBatch()
        }
        relationInsert.executeBatch()
        Vector.empty
      } catch {
        case e: java.sql.BatchUpdateException =>
          e.getUpdateCounts.zipWithIndex
            .filter(element => element._1 == Statement.EXECUTE_FAILED)
            .map { failed =>
              val failedA = argsWithIndex.find(_._2 == failed._2).get._1._1
              val failedB = argsWithIndex.find(_._2 == failed._2).get._1._2
              s"Failure inserting into relationtable ${mutaction.relation.relationTableName} with ids $failedA and $failedB. Cause: ${removeConnectionInfoFromCause(
                e.getCause.toString)}"
            }
            .toVector
        case e: Exception => Vector(e.getCause.toString)
      }

      if (res.nonEmpty) throw new Exception(res.mkString("-@-"))
      res
    }
  }

  def pushScalarListsImport(mutaction: PushScalarListsImport): SimpleDBIO[Vector[String]] = {
    val projectId = mutaction.project.id
    val tableName = mutaction.tableName
    val nodeId    = mutaction.id

    SimpleDBIO[Vector[String]] { x =>
      val setBaseline: Vector[String] = try {
        val query                       = s"set @baseline := ifnull((select max(position) from `$projectId`.`$tableName` where nodeId = ?), 0) + 1000"
        val baseLine: PreparedStatement = x.connection.prepareStatement(query)

        baseLine.setString(1, nodeId)

        baseLine.execute()

        Vector.empty
      } catch {
        case e: Exception => Vector(e.getCause.toString)
      }

      val argsWithIndex = mutaction.args.values.zipWithIndex
      val rowResult: Vector[String] = try {
        val query                         = s"insert into `$projectId`.`$tableName` (`nodeId`, `position`, `value`) values (?, @baseline + ? , ?)"
        val insertRows: PreparedStatement = x.connection.prepareStatement(query)

        argsWithIndex.foreach { argWithIndex =>
          insertRows.setString(1, nodeId)
          insertRows.setInt(2, argWithIndex._2 * 1000)
          insertRows.setGcValue(3, argWithIndex._1)
          insertRows.addBatch()
        }
        insertRows.executeBatch()

        Vector.empty
      } catch {
        case e: java.sql.BatchUpdateException =>
          e.getUpdateCounts.zipWithIndex
            .filter(element => element._1 == Statement.EXECUTE_FAILED)
            .map { failed =>
              val failedValue: GCValue = argsWithIndex.find(_._2 == failed._2).get._1
              s"Failure inserting into listTable $tableName for the id $nodeId for value ${failedValue.value}. Cause: ${removeConnectionInfoFromCause(e.getCause.toString)}"
            }
            .toVector

        case e: Exception => Vector(e.getCause.toString)
      }

      val res = setBaseline ++ rowResult
      if (res.nonEmpty) throw new Exception(res.mkString("-@-"))
      res
    }
  }
}
