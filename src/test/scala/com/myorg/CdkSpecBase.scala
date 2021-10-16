package com.myorg

import com.myorg.lib.{StackArgs, StackWrapper}
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertions.fail
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should
import play.api.libs.json.{JsNull, JsObject, JsValue, Json, Reads, Writes}
import software.amazon.awscdk
import software.amazon.awscdk.core

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.{IterableHasAsScala, MapHasAsScala}
import scala.reflect.{ClassTag, classTag}
import scala.util.control.NonFatal

abstract class CdkSpecBase extends AnyFunSuite with should.Matchers with TypeCheckedTripleEquals {
  import com.myorg.CdkSpecBase._

  protected lazy val testArgs: StackArgs = StackArgs(app)
}

object CdkSpecBase {
  private lazy val app: core.App = new awscdk.core.App

  @annotation.nowarn
  private lazy val BasicTypesWrites: Writes[Any] = {
    case v: JsValue => v
    case v: collection.Map[String, _] =>
      Json.toJson(v.map { case (k, v: Any) => (k, Json.toJson(v)(BasicTypesWrites)) })
    case v: java.util.Map[String, _] => Json.toJson(v.asScala: Any)(BasicTypesWrites)
    case v: collection.Iterable[_]   => Json.toJson(v.map(Json.toJson(_: Any)(BasicTypesWrites)))
    case v: java.lang.Iterable[_]    => Json.toJson(v.asScala: Any)(BasicTypesWrites)
    case v: Array[_]                 => Json.toJson(ArraySeq.unsafeWrapArray(v): Any)(BasicTypesWrites)
    case v: Int                      => Json.toJson(v)
    case v: Short                    => Json.toJson(v)
    case v: Byte                     => Json.toJson(v)
    case v: Long                     => Json.toJson(v)
    case v: Float                    => Json.toJson(v)
    case v: Double                   => Json.toJson(v)
    case v: BigDecimal               => Json.toJson(v)
    case v: BigInt                   => Json.toJson(v)
    case v: java.math.BigInteger     => Json.toJson(v)
    case v: Boolean                  => Json.toJson(v)
    case v: String                   => Json.toJson(v)
    case v: Char                     => Json.toJson(String.valueOf(v))
    case v: java.util.Date           => Json.toJson(v)
    case v: java.time.LocalDate      => Json.toJson(v)
    case v: java.time.LocalDateTime  => Json.toJson(v)
    case v: java.time.LocalTime      => Json.toJson(v)
    case v: java.time.OffsetDateTime => Json.toJson(v)
    case v: java.time.ZonedDateTime  => Json.toJson(v)
    case v: java.time.Instant        => Json.toJson(v)
    case v: java.util.UUID           => Json.toJson(v)
    case v: Some[_]                  => Json.toJson(v.get: Any)(BasicTypesWrites)
    case None                        => JsNull
    case v if v == null              => JsNull
    case _                           => throw new UnsupportedOperationException
  }

  implicit class StackWrapperOps(val value: StackWrapper) extends AnyVal {
    def toJson: JsValue = {
      val template = app.synth.getStackArtifact(value.artifactId).getTemplate
      Json.toJson(template: Any)(BasicTypesWrites)
    }
  }

  implicit class JsValueOps(val value: JsValue) extends AnyVal {
    def get(fieldName: String): JsValue = value match {
      case _: JsObject =>
        try {
          value.apply(fieldName)
        } catch {
          case NonFatal(_) => fail(s"'$fieldName' is undefined on object: ${Json.prettyPrint(value)}")
        }
      case _ => fail(s"cannot get '$fieldName' because ${Json.prettyPrint(value)} is not a JsObject")
    }

    def to[A: ClassTag](implicit fjs: Reads[A]): A = {
      value.validate.fold(
        err => {
          val errors = err.flatMap(_._2).flatMap(_.messages)
          val msg =
            s"${value.getClass.getSimpleName}($value) cannot convert to ${classTag[A].runtimeClass.getSimpleName}"

          fail(errors.mkString("", "\n", "\n") + msg)
        },
        identity,
      )
    }
  }
}
