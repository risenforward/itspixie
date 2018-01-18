package com.prisma.api.mutations.mutations

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.prisma.api.ApiDependencies
import com.prisma.api.database.mutactions.mutactions.ServerSideSubscription
import com.prisma.api.database.mutactions.{ClientSqlMutaction, MutactionGroup, TransactionMutaction}
import com.prisma.api.database.{DataItem, DataResolver}
import com.prisma.api.mutations._
import com.prisma.api.schema.APIErrors
import com.prisma.shared.models.{Model, Project}
import sangria.schema

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Update(
    model: Model,
    project: Project,
    args: schema.Args,
    dataResolver: DataResolver
)(implicit apiDependencies: ApiDependencies)
    extends SingleItemClientMutation {

  implicit val system: ActorSystem             = apiDependencies.system
  implicit val materializer: ActorMaterializer = apiDependencies.materializer

  val coolArgs: CoolArgs = {
    val argsPointer: Map[String, Any] = args.raw.get("data") match {
      case Some(value) => value.asInstanceOf[Map[String, Any]]
      case None        => args.raw
    }
    CoolArgs(argsPointer)
  }

  val where = CoolArgs(args.raw).extractNodeSelectorFromWhereField(model)

  lazy val dataItem: Future[Option[DataItem]] = dataResolver.resolveByUnique(where)

  def prepareMutactions(): Future[List[MutactionGroup]] = {
    dataItem map {
      case Some(dataItem) =>
        val validatedDataItem = dataItem // todo: use GC Values
        // = dataItem.copy(userData = GraphcoolDataTypes.fromSql(dataItem.userData, model.fields))

        val sqlMutactions          = SqlMutactions(dataResolver).getMutactionsForUpdate(coolArgs, dataItem.id, validatedDataItem, where)
        val transactionMutaction   = TransactionMutaction(sqlMutactions, dataResolver)
        val subscriptionMutactions = SubscriptionEvents.extractFromSqlMutactions(project, mutationId, sqlMutactions).toList
        val sssActions             = ServerSideSubscription.extractFromMutactions(project, sqlMutactions, requestId = "").toList

        List(
          MutactionGroup(mutactions = List(transactionMutaction), async = false),
          MutactionGroup(mutactions = sssActions ++ subscriptionMutactions, async = true)
        )

      case None =>
        throw APIErrors.NodeNotFoundForWhereError(where)
    }
  }

  override def getReturnValue: Future[ReturnValueResult] = {
    dataItem flatMap {
      case Some(dataItem) => returnValueByUnique(NodeSelector.forId(model, dataItem.id))
      case None           => Future.successful(NoReturnValue(where))
    }
  }

}
