package com.prisma.api.mutactions

import com.prisma.api.connector._
import com.prisma.gc_values.{NullGCValue, RootGCValue}
import com.prisma.shared.models.IdType.Id
import com.prisma.shared.models.Project

object SubscriptionEvents {
  def extractFromMutactionResults(
      project: Project,
      mutationId: Id,
      mutactionResults: MutactionResults
  ): Vector[PublishSubscriptionEvent] = {
    mutactionResults.results.collect {
      case result: CreateNodeResult => fromCreateResult(project, mutationId, result)
      case result: UpdateNodeResult => fromUpdateResult(project, mutationId, result)
      case result: DeleteNodeResult => fromDeleteResult(project, mutationId, result)
    }
  }

  private def fromCreateResult(project: Project, mutationId: Id, result: CreateNodeResult): PublishSubscriptionEvent = {
    val model = result.mutaction.model
    PublishSubscriptionEvent(
      project = project,
      value = Map("nodeId" -> result.id.value, "modelId" -> model.name, "mutationType" -> "CreateNode"),
      mutationName = s"create${model.name}"
    )
  }

  private def fromUpdateResult(project: Project, mutationId: Id, result: UpdateNodeResult): PublishSubscriptionEvent = {
    val previousValues: Map[String, Any] = result.previousValues.data
      .filterKeys(k => result.namesOfUpdatedFields.contains(k))
      .filterValues(v => v != NullGCValue && !v.isInstanceOf[RootGCValue])
      .toMapStringAny + ("id" -> result.previousValues.id.value)

    val model = result.mutaction.model

    PublishSubscriptionEvent(
      project = project,
      value = Map(
        "nodeId"         -> previousValues("id"),
        "changedFields"  -> result.namesOfUpdatedFields.toList, // must be a List as Vector is printed verbatim
        "previousValues" -> previousValues,
        "modelId"        -> model.name,
        "mutationType"   -> "UpdateNode"
      ),
      mutationName = s"update${model.name}"
    )
  }

  private def fromDeleteResult(project: Project, mutationId: Id, result: DeleteNodeResult): PublishSubscriptionEvent = {
    val previousValues = result.previousValues.data.filterValues(v => v != NullGCValue && !v.isInstanceOf[RootGCValue]).toMapStringAny + ("id" -> result.id)
    val model          = result.mutaction.model

    PublishSubscriptionEvent(
      project = project,
      value = Map("nodeId" -> result.id.value, "node" -> previousValues, "modelId" -> model.name, "mutationType" -> "DeleteNode"),
      mutationName = s"delete${model.name}"
    )
  }

}
