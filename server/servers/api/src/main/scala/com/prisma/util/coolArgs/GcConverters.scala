package com.prisma.util.coolArgs

import java.util.UUID

import com.prisma.api.connector.PrismaArgs
import com.prisma.gc_values._
import com.prisma.shared.models.TypeIdentifier.TypeIdentifier
import com.prisma.shared.models.{Model, ScalarField, TypeIdentifier}
import org.joda.time.DateTime
import org.scalactic.{Bad, Good, Or}
import play.api.libs.json.{JsValue, _}
import sangria.ast._

import scala.util.control.NonFatal

/**
  *  Any <-> GCValue - This is used to transform Sangria arguments
  */
case class GCAnyConverter(typeIdentifier: TypeIdentifier, isList: Boolean) extends GCConverter[Any] {
  import OtherGCStuff._
  import play.api.libs.json._
  override def toGCValue(t: Any): Or[GCValue, InvalidValueForScalarType] = {
    try {
      val result = (t, typeIdentifier) match {
        case (None, _)                                                                => NullGCValue
        case (null, _)                                                                => NullGCValue
        case (_: NullValue, _)                                                        => NullGCValue
        case (x: String, _) if x == "null" && typeIdentifier != TypeIdentifier.String => NullGCValue
        case (x: String, TypeIdentifier.String)                                       => StringGCValue(x)
        case (x: Int, TypeIdentifier.Int)                                             => IntGCValue(x.toInt)
        case (x: BigInt, TypeIdentifier.Int)                                          => IntGCValue(x.toInt)
        case (x: BigInt, TypeIdentifier.Float)                                        => FloatGCValue(x.toDouble)
        case (x: BigDecimal, TypeIdentifier.Float)                                    => FloatGCValue(x.toDouble)
        case (x: Float, TypeIdentifier.Float)                                         => FloatGCValue(x)
        case (x: Double, TypeIdentifier.Float)                                        => FloatGCValue(x)
        case (x: Boolean, TypeIdentifier.Boolean)                                     => BooleanGCValue(x)
        case (x: String, TypeIdentifier.DateTime)                                     => DateTimeGCValue(new DateTime(x))
        case (x: DateTime, TypeIdentifier.DateTime)                                   => DateTimeGCValue(x)
        case (x: String, TypeIdentifier.Cuid)                                         => CuidGCValue(x)
        case (x: UUID, TypeIdentifier.UUID)                                           => UuidGCValue(x)
        case (x: String, TypeIdentifier.Enum)                                         => EnumGCValue(x)
        case (x: JsObject, TypeIdentifier.Json)                                       => JsonGCValue(x)
        case (x: String, TypeIdentifier.Json)                                         => JsonGCValue(Json.parse(x))
        case (x: JsArray, TypeIdentifier.Json)                                        => JsonGCValue(x)
        case (x: List[Any], _) if isList                                              => sequence(x.map(this.toGCValue).toVector).map(seq => ListGCValue(seq)).get
        case _                                                                        => sys.error("Error in toGCValue. Value: " + t)
      }

      Good(result)
    } catch {
      case NonFatal(_) => Bad(InvalidValueForScalarType(t.toString, typeIdentifier.toString))
    }
  }
}

/**
  *  CoolArgs <-> ReallyCoolArgs - This is used to transform from Coolargs for create on a model to typed ReallyCoolArgs
  */
case class GCCreateReallyCoolArgsConverter(model: Model) {

  def toReallyCoolArgs(raw: Map[String, Any]): PrismaArgs = {

    val res = model.scalarNonListFields
      .filter { field =>
        val isIdField = model.idField.contains(field)
        !isIdField || field.typeIdentifier != TypeIdentifier.Int // int id fields are auto generated by the db
      }
      .map { field =>
        val converter = GCAnyConverter(field.typeIdentifier, false)

        val gCValue = raw.get(field.name) match {
          case Some(Some(x)) => converter.toGCValue(x).get
          case Some(None)    => NullGCValue
          case Some(x)       => converter.toGCValue(x).get
          case None          => NullGCValue
        }
        field.name -> gCValue
      }
    PrismaArgs(RootGCValue(res: _*))
  }

  def toReallyCoolArgsFromJson(json: JsValue): PrismaArgs = {

    def fromSingleJsValue(jsValue: JsValue, field: ScalarField): GCValue = jsValue match {
      case JsString(x)                                                    => StringGCValue(x)
      case JsNumber(x) if field.typeIdentifier == TypeIdentifier.Int      => IntGCValue(x.toInt)
      case JsNumber(x) if field.typeIdentifier == TypeIdentifier.Float    => FloatGCValue(x.toDouble)
      case JsBoolean(x) if field.typeIdentifier == TypeIdentifier.Boolean => BooleanGCValue(x)
      case _                                                              => sys.error("Unhandled JsValue")
    }

    val res = model.scalarNonListFields.map { field =>
      val gCValue: JsLookupResult = json \ field.name
      val asOption                = gCValue.toOption
      val converted = asOption match {
        case None                                                              => NullGCValue
        case Some(JsNull)                                                      => NullGCValue
        case Some(JsString(x))                                                 => StringGCValue(x)
        case Some(JsNumber(x)) if field.typeIdentifier == TypeIdentifier.Int   => IntGCValue(x.toInt)
        case Some(JsNumber(x)) if field.typeIdentifier == TypeIdentifier.Float => FloatGCValue(x.toDouble)
        case Some(JsBoolean(x))                                                => BooleanGCValue(x)
        case Some(JsArray(x)) if field.isList                                  => ListGCValue(x.map(v => fromSingleJsValue(v, field)).toVector)
        case Some(x: JsValue) if field.typeIdentifier == TypeIdentifier.Json   => JsonGCValue(x)
        case x                                                                 => sys.error("Not implemented yet: " + x)

      }
      field.name -> converted
    }
    PrismaArgs(RootGCValue(res: _*))
  }
}
