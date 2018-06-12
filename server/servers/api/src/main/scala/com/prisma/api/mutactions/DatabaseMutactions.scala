package com.prisma.api.mutactions

import com.prisma.api.ApiMetrics
import com.prisma.api.connector._
import com.prisma.api.schema.APIErrors.{RelationIsRequired, UpdatingUniqueToNullAndThenNestingMutations}
import com.prisma.gc_values.NullGCValue
import com.prisma.shared.models.{Field, Model, Project, RelationField}
import com.prisma.util.coolArgs._
import cool.graph.cuid.Cuid.createCuid

import scala.collection.immutable.Seq

case class DatabaseMutactions(project: Project) {

  def report[T](mutactions: Vector[T]): Vector[T] = {
    ApiMetrics.mutactionCount.incBy(mutactions.size, project.id)
    mutactions
  }

  def getMutactionsForDelete(path: Path, previousValues: PrismaNode): Vector[DatabaseMutaction] = report {
    Vector(VerifyWhere(project, path.root)) ++ generateCascadingDeleteMutactions(path) ++
      Vector(DeleteRelationCheck(project, path), DeleteDataItem(project, path, previousValues))
  }

  //todo this does not support cascading delete behavior at the moment
  def getMutactionsForDeleteMany(model: Model, whereFilter: Option[Filter]): Vector[DatabaseMutaction] = report {
    val requiredRelationChecks = DeleteManyRelationChecks(project, model, whereFilter)
    val deleteItems            = DeleteDataItems(project, model, whereFilter)
    Vector(requiredRelationChecks, deleteItems)
  }

  def getMutactionsForUpdate(path: Path, args: CoolArgs, previousValues: PrismaNode): Vector[DatabaseMutaction] = report {
    val (nonListArgs, listArgs) = args.getUpdateArgs(path.lastModel)
    val updateMutaction         = UpdateDataItem(project, path, nonListArgs, listArgs, previousValues)
    val whereFieldValue         = args.raw.get(path.root.field.name)
    val updatedWhere            = whereFieldValue.map(updateNodeSelectorValue(path.root)).getOrElse(path.root)
    val updatedPath             = path.copy(root = updatedWhere)

    val nested = getMutactionsForNestedMutation(args, updatedPath, triggeredFromCreate = false)
    if (whereFieldValue.contains(None) && nested.nonEmpty) throw UpdatingUniqueToNullAndThenNestingMutations(path.root.model.name)

    updateMutaction +: nested
  }

  def getMutactionsForUpdateMany(model: Model, whereFilter: Option[Filter], args: CoolArgs): Vector[DatabaseMutaction] = report {
    val (nonListArgs, listArgs) = args.getUpdateArgs(model)
    Vector(UpdateDataItems(project, model, whereFilter, nonListArgs, listArgs))
  }

  def getMutactionsForCreate(path: Path, args: CoolArgs): Vector[DatabaseMutaction] = report {
    val (nonListArgs, listArgs) = args.getCreateArgs(path)
    val createMutaction         = CreateDataItem(project, path, nonListArgs, listArgs)
    val nestedMutactions        = getMutactionsForNestedMutation(args, path, triggeredFromCreate = true)

    createMutaction +: nestedMutactions
  }

  def getMutactionsForUpsert(createPath: Path, updatePath: Path, allArgs: CoolArgs): Vector[DatabaseMutaction] =
    report {
      val (nonListCreateArgs, listCreateArgs) = allArgs.createArgumentsAsCoolArgs.getCreateArgs(createPath)
      val (nonListUpdateArgs, listUpdateArgs) = allArgs.updateArgumentsAsCoolArgs.getUpdateArgs(updatePath.lastModel)

      val createdNestedActions = getNestedMutactionsForUpsert(allArgs.createArgumentsAsCoolArgs, createPath, true)
      val updateNestedActions  = getNestedMutactionsForUpsert(allArgs.updateArgumentsAsCoolArgs, updatePath, false)

      Vector(
        UpsertDataItem(project,
                       createPath,
                       updatePath,
                       nonListCreateArgs,
                       listCreateArgs,
                       nonListUpdateArgs,
                       listUpdateArgs,
                       createdNestedActions,
                       updateNestedActions))
    }

  def getNestedMutactionsForUpsert(args: CoolArgs, path: Path, triggeredFromCreate: Boolean): Vector[DatabaseMutaction] = {
    val x = for {
      field           <- path.relationFieldsNotOnPathOnLastModel
      subModel        = field.relatedModel_!
      nestedMutations = args.subNestedMutation(field, subModel)
    } yield {

      val checkMutactions                 = getMutactionsForWhereChecks(nestedMutations) ++ getMutactionsForConnectionChecks(subModel, nestedMutations, path, field)
      val mutactionsThatACreateCanTrigger = getMutactionsForNestedConnectMutation(nestedMutations, path, field, triggeredFromCreate)
      val otherMutactions = getMutactionsForNestedDisconnectMutation(nestedMutations, path, field) ++ getMutactionsForNestedDeleteMutation(nestedMutations,
                                                                                                                                           path,
                                                                                                                                           field)
      if (triggeredFromCreate && mutactionsThatACreateCanTrigger.isEmpty && field.isRequired) throw RelationIsRequired(field.name, path.lastModel.name)

      checkMutactions ++ mutactionsThatACreateCanTrigger ++ otherMutactions
    }
    x.flatten.toVector
  }

  def getMutactionsForNestedMutation(args: CoolArgs, path: Path, triggeredFromCreate: Boolean): Vector[DatabaseMutaction] = {

    val x = for {
      field           <- path.relationFieldsNotOnPathOnLastModel
      subModel        = field.relatedModel_!
      nestedMutations = args.subNestedMutation(field, subModel)
    } yield {

      val checkMutactions = getMutactionsForWhereChecks(nestedMutations) ++ getMutactionsForConnectionChecks(subModel, nestedMutations, path, field)

      val mutactionsThatACreateCanTrigger = getMutactionsForNestedCreateMutation(subModel, nestedMutations, path, field, triggeredFromCreate) ++
        getMutactionsForNestedConnectMutation(nestedMutations, path, field, triggeredFromCreate)

      val otherMutactions = getMutactionsForNestedDisconnectMutation(nestedMutations, path, field) ++
        getMutactionsForNestedDeleteMutation(nestedMutations, path, field) ++
        getMutactionsForNestedUpdateMutation(nestedMutations, path, field) ++
        getMutactionsForNestedUpsertMutation(nestedMutations, path, field)

      if (triggeredFromCreate && mutactionsThatACreateCanTrigger.isEmpty && field.isRequired) throw RelationIsRequired(field.name, path.lastModel.name)

      checkMutactions ++ mutactionsThatACreateCanTrigger ++ otherMutactions
    }
    x.flatten.toVector
  }

  def getMutactionsForWhereChecks(nestedMutation: NestedMutations): Vector[DatabaseMutaction] = {
    (nestedMutation.updates ++ nestedMutation.deletes ++ nestedMutation.connects ++ nestedMutation.disconnects).collect {
      case x: NestedWhere => VerifyWhere(project, x.where)
    }
  }

  def getMutactionsForConnectionChecks(model: Model, nestedMutation: NestedMutations, path: Path, field: RelationField): Seq[DatabaseMutaction] = {
    (nestedMutation.updates ++ nestedMutation.deletes ++ nestedMutation.disconnects).map(x => VerifyConnection(project, extend(path, field, x)))
  }

  def getMutactionsForNestedCreateMutation(
      model: Model,
      nestedMutation: NestedMutations,
      path: Path,
      field: RelationField,
      triggeredFromCreate: Boolean
  ): Vector[DatabaseMutaction] = {
    nestedMutation.creates.flatMap { create =>
      val extendedPath            = extend(path, field, create).lastEdgeToNodeEdge(NodeSelector.forId(model, createCuid()))
      val (nonListArgs, listArgs) = create.data.getCreateArgs(extendedPath)

      val createMutactions = List(CreateDataItem(project, extendedPath, nonListArgs, listArgs))
      val connectItem      = List(NestedCreateRelation(project, extendedPath, triggeredFromCreate))

      createMutactions ++ connectItem ++ getMutactionsForNestedMutation(create.data, extendedPath, triggeredFromCreate = true)
    }
  }

  def getMutactionsForNestedConnectMutation(
      nestedMutation: NestedMutations,
      path: Path,
      field: RelationField,
      topIsCreate: Boolean
  ): Vector[DatabaseMutaction] = {
    nestedMutation.connects.map(connect => NestedConnectRelation(project, extend(path, field, connect), topIsCreate))
  }

  def getMutactionsForNestedDisconnectMutation(nestedMutation: NestedMutations, path: Path, field: RelationField): Vector[DatabaseMutaction] = {
    nestedMutation.disconnects.map(disconnect => NestedDisconnectRelation(project, extend(path, field, disconnect)))
  }

  def getMutactionsForNestedDeleteMutation(nestedMutation: NestedMutations, path: Path, field: RelationField): Vector[DatabaseMutaction] = {
    nestedMutation.deletes.flatMap { delete =>
      val extendedPath              = extend(path, field, delete)
      val cascadingDeleteMutactions = generateCascadingDeleteMutactions(extendedPath)
      cascadingDeleteMutactions ++ List(DeleteRelationCheck(project, extendedPath), DeleteDataItemNested(project, extendedPath))
    }
  }

  def getMutactionsForNestedUpdateMutation(nestedMutation: NestedMutations, path: Path, field: RelationField): Vector[DatabaseMutaction] = {
    nestedMutation.updates.flatMap { update =>
      val extendedPath            = extend(path, field, update)
      val (nonListArgs, listArgs) = update.data.getUpdateArgs(extendedPath.lastModel)
      val updateMutaction         = NestedUpdateDataItem(project, extendedPath, nonListArgs, listArgs)

      update match {
        case x: UpdateByWhere =>
          val updatedPath = extendedPath.lastEdgeToNodeEdge(currentWhere(x.where, x.data))
          val nested      = getMutactionsForNestedMutation(update.data, updatedPath, triggeredFromCreate = false)
          if (x.where.fieldGCValue == NullGCValue && nested.nonEmpty) throw UpdatingUniqueToNullAndThenNestingMutations(x.where.model.name)
          updateMutaction +: nested

        case _: UpdateByRelation =>
          updateMutaction +: getMutactionsForNestedMutation(update.data, extendedPath, triggeredFromCreate = false)
      }
    }
  }

  def getMutactionsForNestedUpsertMutation(nestedMutation: NestedMutations, path: Path, field: RelationField): Vector[DatabaseMutaction] = {
    nestedMutation.upserts.flatMap { upsert =>
      val extendedPath = extend(path, field, upsert)
      val createWhere  = NodeSelector.forId(extendedPath.lastModel, createCuid())

      val pathForUpdate = upsert match {
        case upsert: UpsertByWhere => extendedPath.lastEdgeToNodeEdge(upsert.where)
        case _: UpsertByRelation   => extendedPath
      }
      val pathForCreate = extendedPath.lastEdgeToNodeEdge(createWhere)

      val (nonListCreateArgs, listCreateArgs) = upsert.create.getCreateArgs(pathForCreate)
      val (nonListUpdateArgs, listUpdateArgs) = upsert.update.getUpdateArgs(pathForUpdate.lastModel)

      val createdNestedActions = getNestedMutactionsForUpsert(upsert.create, pathForCreate, true)
      val updateNestedActions  = getNestedMutactionsForUpsert(upsert.update, pathForUpdate, false)

      Vector(
        UpsertDataItemIfInRelationWith(
          project = project,
          createPath = pathForCreate,
          updatePath = pathForUpdate,
          createListArgs = listCreateArgs,
          createNonListArgs = nonListCreateArgs,
          updateListArgs = listUpdateArgs,
          updateNonListArgs = nonListUpdateArgs,
          createdNestedActions,
          updateNestedActions
        ))
    }
  }

  private def currentWhere(where: NodeSelector, args: CoolArgs) = {
    val whereFieldValue = args.raw.get(where.field.name)
    val updatedWhere    = whereFieldValue.map(updateNodeSelectorValue(where)).getOrElse(where)
    updatedWhere
  }

  def generateCascadingDeleteMutactions(startPoint: Path): Vector[DatabaseMutaction] = {
    def getMutactionsForEdges(paths: Vector[Path]): Vector[DatabaseMutaction] = {
      paths.filter(_.edges.length > startPoint.edges.length) match {
        case x if x.isEmpty =>
          Vector.empty

        case pathsList =>
          val maxPathLength     = pathsList.map(_.edges.length).max
          val longestPaths      = pathsList.filter(_.edges.length == maxPathLength)
          val longestMutactions = longestPaths.map(CascadingDeleteRelationMutactions(project, _))
          val shortenedPaths    = longestPaths.map(_.removeLastEdge)
          val newPaths          = pathsList.filter(_.edges.length < maxPathLength) ++ shortenedPaths

          longestMutactions ++ getMutactionsForEdges(newPaths)
      }
    }

    val paths: Vector[Path] = Path.collectCascadingPaths(startPoint)
    getMutactionsForEdges(paths)
  }

  def extend(path: Path, field: RelationField, nestedMutation: NestedMutation): Path = {
    nestedMutation match {
      case x: NestedWhere => path.append(NodeEdge(field, x.where))
      case _              => path.append(ModelEdge(field))
    }
  }

  def updateNodeSelectorValue(nodeSelector: NodeSelector)(value: Any): NodeSelector = {
    val unwrapped = value match {
      case Some(x) => x
      case x       => x
    }

    val newGCValue = GCAnyConverter(nodeSelector.field.typeIdentifier, isList = false).toGCValue(unwrapped).get
    nodeSelector.copy(fieldGCValue = newGCValue)
  }
}
