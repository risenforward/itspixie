package com.prisma.api.mutations.mutations

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.prisma.api.ApiDependencies
import com.prisma.api.database.DataResolver
import com.prisma.api.database.Types.DataItemFilterCollection
import com.prisma.api.database.mutactions.mutactions.{DeleteDataItems, UpdateDataItems}
import com.prisma.api.database.mutactions.{MutactionGroup, TransactionMutaction}
import com.prisma.api.mutations._
import com.prisma.shared.models.{Model, Project}
import sangria.schema

import scala.concurrent.Future

case class UpdateMany(
    project: Project,
    model: Model,
    args: schema.Args,
    where: DataItemFilterCollection,
    dataResolver: DataResolver
)(implicit apiDependencies: ApiDependencies)
    extends ClientMutation[BatchPayload] {

  import apiDependencies.system.dispatcher

  val count = dataResolver.countByModel(model, where)
  val coolArgs: CoolArgs = {
    val argsPointer: Map[String, Any] = args.raw.get("data") match {
      case Some(value) => value.asInstanceOf[Map[String, Any]]
      case None        => args.raw
    }
    CoolArgs(argsPointer)
  }

  def prepareMutactions(): Future[List[MutactionGroup]] = {
    for {
      _ <- count // make sure that count query has been resolved before proceeding
    } yield {
      val updateItems          = UpdateDataItems(project, model, coolArgs, where)
      val transactionMutaction = TransactionMutaction(List(updateItems), dataResolver)
      List(
        MutactionGroup(mutactions = List(transactionMutaction), async = false)
      )
    }
  }

  override def getReturnValue: Future[BatchPayload] = {
    for {
      count <- count
    } yield {
      BatchPayload(count = count)
    }
  }

}
