package com.prisma.api.resolver

import com.prisma.api.connector.{DataResolver, PrismaNode}
import com.prisma.api.resolver.DeferredTypes._
import com.prisma.shared.models.Project

import scala.concurrent.ExecutionContext

class OneDeferredResolver(dataResolver: DataResolver) {
  def resolve(orderedDeferreds: Vector[OrderedDeferred[OneDeferred]],
              executionContext: ExecutionContext): Vector[OrderedDeferredFutureResult[OneDeferredResultType]] = {
    implicit val ec: ExecutionContext = executionContext
    val deferreds                     = orderedDeferreds.map(_.deferred)

    // check if we really can satisfy all deferreds with one database query
    DeferredUtils.checkSimilarityOfOneDeferredsAndThrow(deferreds)

    val headDeferred = deferreds.head

    // fetch prismaNodes
    val futurePrismaNodes =
      dataResolver.batchResolveByUnique(headDeferred.model, headDeferred.where.field, deferreds.map(deferred => deferred.where.fieldValue))

    // assign the prismaNode that was requested by each deferred
    orderedDeferreds.map {
      case OrderedDeferred(deferred, order) =>
        OrderedDeferredFutureResult[OneDeferredResultType](futurePrismaNodes.map { vector =>
          prismaNodesToToOneDeferredResultType(dataResolver.project, deferred, vector)
        }, order)
    }
  }

  private def prismaNodesToToOneDeferredResultType(project: Project, deferred: OneDeferred, prismaNodes: Vector[PrismaNode]): Option[PrismaNode] = {
    deferred.where.fieldName match {
      case "id" => prismaNodes.find(_.id == deferred.where.fieldValue)
      case _    => prismaNodes.find(prismaNode => deferred.where.fieldValue == prismaNode.data.map(deferred.where.fieldName))
    }
  }
}
