package com.prisma.api.resolver

import com.prisma.api.connector.{DataResolver, PrismaNode, PrismaNodeWithParent}
import com.prisma.api.resolver.DeferredTypes.{OneDeferredResultType, OrderedDeferred, OrderedDeferredFutureResult, FromOneDeferred}
import com.prisma.shared.models.Project

import scala.concurrent.{ExecutionContext, Future}

class FromOneDeferredResolver(dataResolver: DataResolver) {
  def resolve(
      orderedDeferreds: Vector[OrderedDeferred[FromOneDeferred]]
  )(implicit ec: ExecutionContext): Vector[OrderedDeferredFutureResult[OneDeferredResultType]] = {
    val deferreds = orderedDeferreds.map(_.deferred)

    // check if we really can satisfy all deferreds with one database query
    DeferredUtils.checkSimilarityOfRelatedDeferredsAndThrow(deferreds)

    val headDeferred   = deferreds.head
    val relatedField   = headDeferred.relationField
    val args           = headDeferred.args
    val selectedFields = headDeferred.selectedFields

    // get ids of prismaNodes in related model we need to fetch
    val relatedModelIds = deferreds.map(deferred => deferred.parentNodeId)

    // fetch prismaNodes
    val futurePrismaNodes: Future[Vector[PrismaNodeWithParent]] =
      dataResolver.getRelatedNodes(relatedField, relatedModelIds, args, selectedFields).map(_.flatMap(_.nodes))

    // assign the prismaNode that was requested by each deferred
    orderedDeferreds.map {
      case OrderedDeferred(deferred, order) =>
        OrderedDeferredFutureResult[OneDeferredResultType](futurePrismaNodes.map { nodes =>
          prismaNodesToToOneDeferredResultType(dataResolver.project, deferred, nodes)
        }, order)
    }
  }

  private def prismaNodesToToOneDeferredResultType(project: Project, deferred: FromOneDeferred, nodes: Vector[PrismaNodeWithParent]): Option[PrismaNode] = {
    def matchesRelation(prismaNodeWithParent: PrismaNodeWithParent, relationSide: String) = prismaNodeWithParent.parentId == deferred.parentNodeId

    nodes.find(node => matchesRelation(node, deferred.relationField.relationSide.toString)).map(_.prismaNode)
  }
}
