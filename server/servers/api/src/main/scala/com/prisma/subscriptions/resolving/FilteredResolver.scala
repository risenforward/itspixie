package com.prisma.subscriptions.resolving

import com.prisma.api.connector.Types.DataItemFilterCollection
import com.prisma.api.connector._
import com.prisma.api.schema.ObjectTypeBuilder
import com.prisma.gc_values.IdGCValue
import com.prisma.shared.models.Model
import sangria.schema.Context

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FilteredResolver {
  def resolve(
      modelObjectTypes: ObjectTypeBuilder,
      model: Model,
      id: String,
      ctx: Context[_, Unit],
      dataResolver: DataResolver
  ): Future[Option[PrismaNode]] = {

    val filterInput: DataItemFilterCollection = modelObjectTypes
      .extractQueryArgumentsFromContextForSubscription(model = model, ctx = ctx)
      .flatMap(_.filter)
      .getOrElse(List.empty)

    def removeTopLevelIdFilter(element: Any) =
      element match {
        case e: FilterElement => e.key != "id"
        case _                => true
      }

    val filter = filterInput.filter(removeTopLevelIdFilter(_)) ++ List(
      FinalValueFilter(key = "id", value = IdGCValue(id), field = model.getScalarFieldByName_!("id"), ""))

    dataResolver.resolveByModel(model, Some(QueryArguments.filterOnly(filter = Some(filter)))).map(_.nodes.headOption)
  }
}
