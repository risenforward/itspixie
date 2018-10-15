package com.prisma.api.connector.mongo.impl

import com.prisma.api.connector._
import com.prisma.api.connector.mongo.database._
import com.prisma.api.connector.mongo.extensions.SlickReplacement._
import com.prisma.api.connector.mongo.{NestedDatabaseMutactionInterpreter, TopLevelDatabaseMutactionInterpreter}
import com.prisma.gc_values.IdGCValue
import org.mongodb.scala.{MongoClient, MongoDatabase}
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

class MongoDatabaseMutactionExecutor(client: MongoClient)(implicit ec: ExecutionContext) extends DatabaseMutactionExecutor {

  override def executeTransactionally(mutaction: TopLevelDatabaseMutaction): Future[MutactionResults] = execute(mutaction, transactionally = true)

  override def executeNonTransactionally(mutaction: TopLevelDatabaseMutaction): Future[MutactionResults] = execute(mutaction, transactionally = false)

  private def execute(mutaction: TopLevelDatabaseMutaction, transactionally: Boolean): Future[MutactionResults] = {
    val actionsBuilder = MongoActionsBuilder(mutaction.project.id, client)
    val action         = generateTopLevelMutaction(actionsBuilder.database, mutaction, actionsBuilder)

    run(actionsBuilder.database, action)
  }

  def generateTopLevelMutaction(
      database: MongoDatabase,
      mutaction: TopLevelDatabaseMutaction,
      mutationBuilder: MongoActionsBuilder
  ): MongoAction[MutactionResults] = {
    mutaction match {
      case m: TopLevelUpsertNode =>
        for {
          result <- interpreterFor(m).mongoActionWithErrorMapped(mutationBuilder)
          childResult <- generateTopLevelMutaction(database,
                                                   result.results.head.asInstanceOf[UpsertNodeResult].result.asInstanceOf[TopLevelDatabaseMutaction],
                                                   mutationBuilder)
        } yield result.merge(childResult)

      case m: FurtherNestedMutaction =>
        for {
          result <- interpreterFor(m).mongoActionWithErrorMapped(mutationBuilder)
          childResults <- result match {
                           case results: MutactionResults =>
                             val nestedMutactions =
                               m.allNestedMutactions.map(x => generateNestedMutaction(database, x, results, results.find(m).id, mutationBuilder))
                             MongoAction.seq(nestedMutactions)
                           case _ => MongoAction.successful(Vector.empty)
                         }
        } yield result.merge(childResults)

      case m: FinalMutaction =>
        for {
          result <- interpreterFor(m).mongoActionWithErrorMapped(mutationBuilder)
        } yield result

      case _ => sys.error("not implemented yet")
    }
  }

  def generateNestedMutaction(
      database: MongoDatabase,
      mutaction: NestedDatabaseMutaction,
      previousResults: MutactionResults,
      parentId: IdGCValue,
      mutationBuilder: MongoActionsBuilder
  ): MongoAction[MutactionResults] = {
    mutaction match {
      case m: NestedUpsertNode =>
        for {
          result <- interpreterFor(m).mongoActionWithErrorMapped(mutationBuilder, parentId)
          childResult <- generateNestedMutaction(
                          database,
                          result.results.head.asInstanceOf[UpsertNodeResult].result.asInstanceOf[NestedDatabaseMutaction],
                          previousResults.merge(result),
                          parentId,
                          mutationBuilder
                        )
        } yield previousResults.merge(result).merge(childResult)

      case m: FurtherNestedMutaction =>
        if (previousResults.contains(m)) {
          val nestedMutactions =
            m.allNestedMutactions.map(x => generateNestedMutaction(database, x, previousResults, previousResults.find(m).id, mutationBuilder))

          for {
            childResults <- MongoAction.seq(nestedMutactions)
          } yield previousResults.merge(childResults)

        } else {
          for {
            result <- interpreterFor(m).mongoActionWithErrorMapped(mutationBuilder, parentId)
            childResults <- result match {
                             case results: MutactionResults =>
                               val nestedMutactions =
                                 m.allNestedMutactions.map(x =>
                                   generateNestedMutaction(database, x, previousResults.merge(result), results.find(m).id, mutationBuilder))
                               MongoAction.seq(nestedMutactions)
                             case _ => MongoAction.successful(Vector.empty)
                           }
          } yield previousResults.merge(result).merge(childResults)
        }
      case m: FinalMutaction =>
        if (previousResults.contains(m)) {
          MongoAction.successful(previousResults)
        } else {
          for {
            result <- interpreterFor(m).mongoActionWithErrorMapped(mutationBuilder, parentId)
          } yield result
        }
      case _ => sys.error("not implemented yet")
    }
  }

  def interpreterFor(mutaction: TopLevelDatabaseMutaction): TopLevelDatabaseMutactionInterpreter = mutaction match {
    case m: TopLevelCreateNode => CreateNodeInterpreter(mutaction = m)
    case m: TopLevelUpdateNode => UpdateNodeInterpreter(mutaction = m)
    case m: TopLevelUpsertNode => UpsertNodeInterpreter(mutaction = m)
    case m: TopLevelDeleteNode => DeleteNodeInterpreter(mutaction = m)
    case m: UpdateNodes        => UpdateNodesInterpreter(mutaction = m)
    case m: DeleteNodes        => DeleteNodesInterpreter(mutaction = m)
    case m: ResetData          => ResetDataInterpreter(mutaction = m)
    case m: ImportNodes        => ??? //delayed
    case m: ImportRelations    => ??? //delayed
    case m: ImportScalarLists  => ??? //delayed
  }

  //for embedded types none of these should actually fire since they should be embedded in their toplevel actions
  def interpreterFor(mutaction: NestedDatabaseMutaction): NestedDatabaseMutactionInterpreter = mutaction match {
    case m: NestedCreateNode => NestedCreateNodeInterpreter(mutaction = m)
    case m: NestedUpdateNode => NestedUpdateNodeInterpreter(mutaction = m)
    case m: NestedUpsertNode => NestedUpsertNodeInterpreter(mutaction = m)
    case m: NestedDeleteNode => NestedDeleteNodeInterpreter(mutaction = m)
    case m: NestedConnect    => NestedConnectInterpreter(mutaction = m)
    case m: NestedDisconnect => NestedDisconnectInterpreter(mutaction = m)
  }

  override def executeRaw(query: String): Future[JsValue] = Future.successful(Json.obj("notImplemented" -> true))
}
