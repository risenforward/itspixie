package com.prisma.api.connector

import com.prisma.api.connector.Types.DataItemFilterCollection
import com.prisma.gc_values.GCValue
import com.prisma.shared.models.{Field, Model, Project}

import scala.concurrent.Future

trait DataResolver {
  def project: Project

  def resolveByGlobalId(globalId: String): Future[Option[PrismaNode]]

  def resolveByModel(model: Model, args: Option[QueryArguments] = None): Future[ResolverResultNew[PrismaNode]]

  def resolveByUnique(where: NodeSelector): Future[Option[PrismaNode]]

  def countByModel(model: Model, where: Option[DataItemFilterCollection] = None): Future[Int]

  def batchResolveByUnique(model: Model, key: String, values: Vector[GCValue]): Future[Vector[PrismaNode]]

  def batchResolveScalarList(model: Model, field: Field, nodeIds: Vector[String]): Future[Vector[ScalarListValues]]

  def resolveByRelationManyModels(fromField: Field,
                                  fromModelIds: Vector[String],
                                  args: Option[QueryArguments]): Future[Vector[ResolverResultNew[PrismaNodeWithParent]]]

  def countByRelationManyModels(fromField: Field, fromNodeIds: Vector[String], args: Option[QueryArguments]): Future[Vector[(String, Int)]]

  def loadListRowsForExport(model: Model, field: Field, args: Option[QueryArguments] = None): Future[ResolverResultNew[ScalarListValues]]

  def loadRelationRowsForExport(relationId: String, args: Option[QueryArguments] = None): Future[ResolverResultNew[RelationNode]]

}
