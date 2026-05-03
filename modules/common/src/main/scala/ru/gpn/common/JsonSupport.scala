package ru.gpn.common

import zio.json.*

object JsonSupport:
  private val uuidEncoder: JsonEncoder[java.util.UUID] =
    JsonEncoder[String].contramap(_.toString)

  private val uuidDecoder: JsonDecoder[java.util.UUID] =
    JsonDecoder[String].mapOrFail { value =>
      scala.util.Try(java.util.UUID.fromString(value)).toEither.left.map(_ => s"Invalid UUID: $value")
    }

  given JsonEncoder[java.util.UUID] = uuidEncoder
  given JsonDecoder[java.util.UUID] = uuidDecoder
  given JsonCodec[java.util.UUID] =
    JsonCodec(uuidEncoder, uuidDecoder)
