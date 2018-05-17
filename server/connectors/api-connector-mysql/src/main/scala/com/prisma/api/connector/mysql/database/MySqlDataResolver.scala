package com.prisma.api.connector.mysql.database

import com.prisma.api.connector.Types.DataItemFilterCollection
import com.prisma.api.connector._
import com.prisma.api.connector.mysql.Metrics
import com.prisma.gc_values._
import com.prisma.shared.models._
import slick.dbio.Effect.Read
import slick.dbio.NoStream
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery
import slick.sql.SqlAction

import scala.concurrent.{ExecutionContext, Future}

case class MySqlDataResolver(project: Project, readonlyClientDatabase: MySQLProfile.backend.DatabaseDef)(implicit ec: ExecutionContext) extends DataResolver {
  val queryBuilder = new MySqlDatabaseQueryBuilder()(ec)

  override def resolveByGlobalId(globalId: IdGCValue): Future[Option[PrismaNode]] = { //todo rewrite this to use normal query?
    if (globalId.value == "viewer-fixed") return Future.successful(Some(PrismaNode(globalId, RootGCValue.empty, Some("Viewer"))))

    val query: SqlAction[Option[String], NoStream, Read] = TableQuery(new ProjectRelayIdTable(_, project.id))
      .filter(_.id === globalId.value)
      .map(_.stableModelIdentifier)
      .take(1)
      .result
      .headOption

    readonlyClientDatabase
      .run(query)
      .flatMap {
        case Some(stableModelIdentifier) =>
          val model = project.schema.getModelByStableIdentifier_!(stableModelIdentifier.trim)
          resolveByUnique(NodeSelector.forIdGCValue(model, globalId))

        case _ =>
          Future.successful(None)
      }
  }

  override def resolveByModel(model: Model, args: Option[QueryArguments] = None): Future[ResolverResult[PrismaNode]] = {
    val query = queryBuilder.selectAllFromTable(project.id, model, args)
    performWithTiming("loadModelRowsForExport", readonlyClientDatabase.run(query))
  }

  override def resolveByUnique(where: NodeSelector): Future[Option[PrismaNode]] =
    batchResolveByUnique(where.model, where.field, Vector(where.fieldValue)).map(_.headOption)

  override def countByTable(table: String, whereFilter: Option[DataItemFilterCollection] = None): Future[Int] = {
    val query = queryBuilder.countAllFromTable(project, table, whereFilter)
    performWithTiming("countByModel", readonlyClientDatabase.run(query))
  }

  override def batchResolveByUnique(model: Model, field: Field, values: Vector[GCValue]): Future[Vector[PrismaNode]] = {
    val query = queryBuilder.batchSelectFromModelByUnique(project.id, model, field.dbName, values)
    performWithTiming("batchResolveByUnique", readonlyClientDatabase.run(query))
  }

  override def batchResolveScalarList(model: Model, listField: Field, nodeIds: Vector[IdGCValue]): Future[Vector[ScalarListValues]] = {
    val query = queryBuilder.selectFromScalarList(project.id, model.name, listField, nodeIds)
    performWithTiming("batchResolveScalarList", readonlyClientDatabase.run(query))
  }

  override def resolveByRelationManyModels(fromField: Field,
                                           fromNodeIds: Vector[IdGCValue],
                                           args: Option[QueryArguments]): Future[Vector[ResolverResult[PrismaNodeWithParent]]] = {
    val query = queryBuilder.batchSelectAllFromRelatedModel(project, fromField, fromNodeIds, args)
    performWithTiming("resolveByRelation", readonlyClientDatabase.run(query))
  }

  override def countByRelationManyModels(fromField: Field, fromNodeIds: Vector[IdGCValue], args: Option[QueryArguments]): Future[Vector[(IdGCValue, Int)]] = {
    val query = queryBuilder.countAllFromRelatedModels(project, fromField, fromNodeIds, args)
    performWithTiming("countByRelation", readonlyClientDatabase.run(query))
  }

  override def loadListRowsForExport(model: Model, field: Field, args: Option[QueryArguments] = None): Future[ResolverResult[ScalarListValues]] = {
    val query = queryBuilder.selectAllFromListTable(project.id, model, field, args, None)
    performWithTiming("loadListRowsForExport", readonlyClientDatabase.run(query))
  }

  override def loadRelationRowsForExport(relationId: String, args: Option[QueryArguments] = None): Future[ResolverResult[RelationNode]] = {
    val query = queryBuilder.selectAllFromRelationTable(project.id, relationId, args)
    performWithTiming("loadRelationRowsForExport", readonlyClientDatabase.run(query))
  }

  protected def performWithTiming[A](name: String, f: => Future[A]): Future[A] = Metrics.sqlQueryTimer.timeFuture(project.id, name) { f }
}
