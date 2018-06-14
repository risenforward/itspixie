package com.prisma.api.mutations

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.prisma.api.ApiDependencies
import com.prisma.api.connector._
import com.prisma.api.mutactions.{DatabaseMutactions, NodeIds, ServerSideSubscriptions, SubscriptionEvents}
import com.prisma.gc_values.CuidGCValue
import com.prisma.shared.models.IdType.Id
import com.prisma.shared.models._
import com.prisma.util.coolArgs.CoolArgs
import cool.graph.cuid.Cuid
import sangria.schema

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Create(
    model: Model,
    project: Project,
    args: schema.Args,
    dataResolver: DataResolver
)(implicit apiDependencies: ApiDependencies)
    extends SingleItemClientMutation {

  implicit val system: ActorSystem             = apiDependencies.system
  implicit val materializer: ActorMaterializer = apiDependencies.materializer

  val id                = NodeIds.createNodeIdForModel(model)
  val requestId: String = "" //                        = dataResolver.requestContext.map(_.requestId).getOrElse("")

  val coolArgs: CoolArgs = CoolArgs.fromSchemaArgs(args.raw)

  val path = Path.empty(NodeSelector.forIdGCValue(model, id))

  def prepareMutactions(): Future[TopLevelDatabaseMutaction] = {
    val createMutactionsResult = DatabaseMutactions(project).getMutactionsForCreate(path, coolArgs)
//    val subscriptionMutactions = SubscriptionEvents.extractFromSqlMutactions(project, mutationId, createMutactionsResult)
//    val sssActions             = ServerSideSubscriptions.extractFromMutactions(project, createMutactionsResult, requestId)

    Future.successful(createMutactionsResult)
  }

  override def getReturnValue(results: MutactionResults): Future[ReturnValueResult] = {
    val createdItem = results.databaseResults.collectFirst { case x: CreateDataItemResult => x }
    for {
      returnValue <- returnValueByUnique(NodeSelector.forIdGCValue(model, createdItem.map(_.id).getOrElse(id)))
      prismaNode  = returnValue.asInstanceOf[ReturnValue].prismaNode
    } yield {
      ReturnValue(prismaNode)
    }
  }
}
