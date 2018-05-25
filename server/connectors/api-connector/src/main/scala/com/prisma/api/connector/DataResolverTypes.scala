package com.prisma.api.connector

import com.prisma.gc_values.{GCValue, IdGCValue}
import com.prisma.shared.models.IdType.Id
import com.prisma.shared.models._

case class ScalarListElement(nodeId: Id, position: Int, value: GCValue)

case class ResolverResult[T](
    nodes: Vector[T],
    hasNextPage: Boolean,
    hasPreviousPage: Boolean,
    parentModelId: Option[IdGCValue] = None
)

case class QueryArguments(
    skip: Option[Int],
    after: Option[String],
    first: Option[Int],
    before: Option[String],
    last: Option[Int],
    filter: Option[Filter],
    orderBy: Option[OrderBy]
)

object QueryArguments {
  def empty                              = QueryArguments(skip = None, after = None, first = None, before = None, last = None, filter = None, orderBy = None)
  def filterOnly(filter: Option[Filter]) = QueryArguments.empty.copy(filter = filter)
}

object SortOrder extends Enumeration {
  type SortOrder = Value
  val Asc: SortOrder.Value  = Value("asc")
  val Desc: SortOrder.Value = Value("desc")
}

case class OrderBy(
    field: ScalarField,
    sortOrder: SortOrder.Value
)

object LogicalKeyWords {
  val logicCombinators           = List("AND", "OR", "NOT")
  def isLogicFilter(key: String) = logicCombinators.contains(key)
}

sealed trait Filter

case class AndFilter(filters: Vector[Filter])  extends Filter
case class OrFilter(filters: Vector[Filter])   extends Filter
case class NotFilter(filters: Vector[Filter])  extends Filter
case class NodeFilter(filters: Vector[Filter]) extends Filter

case class ScalarFilter(field: ScalarField, condition: ScalarCondition) extends Filter

sealed trait ScalarCondition
case class Equals(value: GCValue)              extends ScalarCondition
case class NotEquals(value: GCValue)           extends ScalarCondition
case class Contains(value: GCValue)            extends ScalarCondition
case class NotContains(value: GCValue)         extends ScalarCondition
case class StartsWith(value: GCValue)          extends ScalarCondition
case class NotStartsWith(value: GCValue)       extends ScalarCondition
case class EndsWith(value: GCValue)            extends ScalarCondition
case class NotEndsWith(value: GCValue)         extends ScalarCondition
case class LessThan(value: GCValue)            extends ScalarCondition
case class LessThanOrEquals(value: GCValue)    extends ScalarCondition
case class GreaterThan(value: GCValue)         extends ScalarCondition
case class GreaterThanOrEquals(value: GCValue) extends ScalarCondition
case class In(values: Vector[GCValue])         extends ScalarCondition
case class NotIn(values: Vector[GCValue])      extends ScalarCondition

case class ScalarListFilter(key: String, field: Field, condition: ScalarListCondition) extends Filter

sealed trait ScalarListCondition
case class ListContains(value: GCValue)              extends ScalarListCondition
case class ListContainsEvery(value: Vector[GCValue]) extends ScalarListCondition
case class ListContainsSome(value: Vector[GCValue])  extends ScalarListCondition

case class OneRelationIsNullFilter(field: RelationField) extends Filter

case class RelationFilter(field: RelationField, nestedFilter: Filter, condition: RelationCondition) extends Filter

sealed trait RelationCondition
object EveryRelatedNode      extends RelationCondition
object AtLeastOneRelatedNode extends RelationCondition
object NoRelatedNode         extends RelationCondition
object NoRelationCondition   extends RelationCondition

case class NodeSubscriptionFilter()                        extends Filter
case class PreComputedSubscriptionFilter(boolean: Boolean) extends Filter
