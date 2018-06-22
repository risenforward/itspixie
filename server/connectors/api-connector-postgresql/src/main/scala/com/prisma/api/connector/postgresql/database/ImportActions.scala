package com.prisma.api.connector.postgresql.database

import java.sql.{PreparedStatement, Statement}

import com.prisma.api.connector.postgresql.database.JooqQueryBuilders.placeHolder
import com.prisma.api.connector.{CreateDataItemsImport, CreateRelationRowsImport, PushScalarListsImport}
import com.prisma.gc_values.{GCValue, IdGCValue, ListGCValue, NullGCValue}
import com.prisma.slick.NewJdbcExtensions.ReadsResultSet
import cool.graph.cuid.Cuid
import org.jooq.impl.DSL.max
import slick.jdbc.PositionedParameters

trait ImportActions extends BuilderBase {
  import com.prisma.slick.NewJdbcExtensions._
  import com.prisma.api.connector.postgresql.database.JdbcExtensions._
  import com.prisma.api.connector.postgresql.database.PostgresSlickExtensions._
  import slick.jdbc.PostgresProfile.api._

  def createDataItemsImport(mutaction: CreateDataItemsImport): SimpleDBIO[Vector[String]] = {

    SimpleDBIO[Vector[String]] { jdbcActionContext =>
      val model         = mutaction.model
      val argsWithIndex = mutaction.args.zipWithIndex

      val nodeResult: Vector[String] = try {
        val columns      = model.scalarNonListFields.map(_.dbName)
        val escapedKeys  = columns.map(column => s""""$column"""").mkString(",")
        val placeHolders = columns.map(_ => "?").mkString(",")

        val query                         = s"""INSERT INTO "$schemaName"."${model.dbName}" ($escapedKeys) VALUES ($placeHolders)"""
        val itemInsert: PreparedStatement = jdbcActionContext.connection.prepareStatement(query)
        val currentTimeStamp              = currentTimeStampUTC

        //Fixme have a helper for adding updatedAt / createdAt
        mutaction.args.foreach { arg =>
          val argsAsRoot = arg.raw.asRoot
          model.scalarNonListFields.zipWithIndex.foreach {
            case (field, index) =>
              argsAsRoot.map.get(field.name) match {
                case Some(NullGCValue) if field.name == "createdAt" || field.name == "updatedAt" => itemInsert.setTimestamp(index + 1, currentTimeStamp)
                case Some(gCValue)                                                               => itemInsert.setGcValue(index + 1, gCValue)
                case None if field.name == "createdAt" || field.name == "updatedAt"              => itemInsert.setTimestamp(index + 1, currentTimeStamp)
                case None                                                                        => itemInsert.setNull(index + 1, java.sql.Types.NULL)
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
              s"Failure inserting ${model.dbName} with Id: $failedId. Cause: ${removeConnectionInfoFromCause(e.getCause.toString)}"
            }
            .toVector
        case e: Exception => Vector(e.getCause.toString)
      }

      val relayResult: Vector[String] = try {
        val relayQuery                     = s"""INSERT INTO "$schemaName"."_RelayId" ("id", "stableModelIdentifier") VALUES (?,?)"""
        val relayInsert: PreparedStatement = jdbcActionContext.connection.prepareStatement(relayQuery)

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
        case e: Exception => Vector(e.getMessage)
      }

      val res = nodeResult ++ relayResult
      if (res.nonEmpty) throw new Exception(res.mkString("-@-"))
      res
    }
  }

  def removeConnectionInfoFromCause(cause: String): String = {
    val connectionSubStringStart = cause.indexOf(": ERROR:")
    cause.substring(connectionSubStringStart + 9)

  }

  def createRelationRowsImport(mutaction: CreateRelationRowsImport): SimpleDBIO[Vector[String]] = {
    val argsWithIndex: Seq[((IdGCValue, IdGCValue), Int)] = mutaction.args.zipWithIndex

    SimpleDBIO[Vector[String]] { x =>
      val res = try {
        val query                             = s"""INSERT INTO "$schemaName"."${mutaction.relation.relationTableName}" ("id", "A","B") VALUES (?,?,?)"""
        val relationInsert: PreparedStatement = x.connection.prepareStatement(query)
        mutaction.args.foreach { arg =>
          relationInsert.setString(1, Cuid.createCuid())
          relationInsert.setGcValue(2, arg._1)
          relationInsert.setGcValue(3, arg._2)
          relationInsert.addBatch()
        }
        relationInsert.executeBatch()
        Vector.empty
      } catch {
        case e: java.sql.BatchUpdateException =>
          val faileds = e.getUpdateCounts.zipWithIndex

          faileds
            .filter(element => element._1 == Statement.EXECUTE_FAILED)
            .map { failed =>
              val failedA = argsWithIndex.find(_._2 == failed._2).get._1._1
              val failedB = argsWithIndex.find(_._2 == failed._2).get._1._2
              s"Failure inserting into relationtable ${mutaction.relation.relationTableName} with ids $failedA and $failedB. Cause: ${removeConnectionInfoFromCause(
                e.getCause.toString)}"
            }
            .toVector
        case e: Exception => Vector(e.getMessage)
      }

      if (res.nonEmpty) throw new Exception(res.mkString("-@-"))
      res
    }
  }

  def pushScalarListsImport(mutactionArg: PushScalarListsImport) = {
    val field = mutactionArg.field

    val mutaction = {
      val x: Map[IdGCValue, ListGCValue] = mutactionArg.valueTuples.groupBy(_._1).mapValues { values =>
        val listGcValues  = values.map(_._2)
        val combinedValue = listGcValues.foldLeft(ListGCValue(Vector.empty))(_ ++ _)
        combinedValue
      }
      mutactionArg.copy(valueTuples = x.toVector)
    }

    val positions: DBIO[Map[IdGCValue, Int]] = SimpleDBIO[Map[IdGCValue, Int]] { ctx =>
      val nodeIdField  = scalarListColumn(field, "nodeId")
      val placeholders = mutaction.valueTuples.map(_ => placeHolder)

      val query = sql
        .select(nodeIdField, max(scalarListColumn(field, "position")))
        .from(scalarListTable(field))
        .groupBy(nodeIdField)
        .having(nodeIdField.in(placeholders: _*))

      val ps = ctx.connection.prepareStatement(query.getSQL)

      val pp = new PositionedParameters(ps)

      mutaction.valueTuples.map(_._1).foreach(pp.setGcValue)
      val rs = ps.executeQuery()
      val reads = ReadsResultSet { rs =>
        val nodeId = rs.getId(field.model, "nodeId")
        val start  = rs.getInt("max")
        (nodeId, start)
      }
      rs.as(reads).toMap
    }

    // id, listvalue, start
    def push1(values: Iterable[(IdGCValue, ListGCValue, Int)]): Iterable[(IdGCValue, GCValue, Int)] = values.flatMap {
      case (id, list, start) =>
        list.values.zipWithIndex.map { case (value, index) => (id, value, start + (index * 1000)) }
    }

    // id, value, positions
    def push2(values: Iterable[(IdGCValue, GCValue, Int)]) = SimpleDBIO[Unit] { ctx =>
      val query = sql
        .insertInto(scalarListTable(field))
        .columns(scalarListColumn(field, "nodeId"), scalarListColumn(field, "value"), scalarListColumn(field, "position"))
        .values(placeHolder, placeHolder, placeHolder)

      val ps: PreparedStatement = ctx.connection.prepareStatement(query.getSQL)
      // do it
      values.foreach { value =>
        ps.setGcValue(1, value._1)
        ps.setGcValue(2, value._2)
        ps.setInt(3, value._3)
        ps.addBatch()
      }
      ps.executeBatch()
    }

    import scala.concurrent.ExecutionContext.Implicits.global

    for {
      positions <- positions
      x = mutaction.valueTuples.map {
        case (id, listValue) =>
          val start = positions.get(id).getOrElse(0)
          (id, listValue, start)
      }
      y = push1(x)
      _ <- push2(y)
    } yield Vector.empty
  }
}
