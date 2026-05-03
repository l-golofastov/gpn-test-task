package ru.gpn.common

import zio.json.*

sealed trait AppError extends Exception:
  def status: Int
  def code: String
  def detail: String
  override final def getMessage: String = detail

object AppError:
  final case class Validation(detail: String) extends AppError:
    val status = 400
    val code = "validation_error"

  final case class NotFound(detail: String) extends AppError:
    val status = 404
    val code = "not_found"

  final case class Conflict(detail: String) extends AppError:
    val status = 409
    val code = "conflict"

  final case class External(detail: String) extends AppError:
    val status = 502
    val code = "external_service_error"

  final case class Database(detail: String) extends AppError:
    val status = 500
    val code = "database_error"

  final case class Config(detail: String) extends AppError:
    val status = 500
    val code = "config_error"

  final case class FileSystem(detail: String) extends AppError:
    val status = 500
    val code = "filesystem_error"

  final case class Unexpected(detail: String) extends AppError:
    val status = 500
    val code = "unexpected_error"

  def fromThrowable(error: Throwable): AppError =
    error match
      case app: AppError => app
      case other         => Unexpected(Option(other.getMessage).getOrElse(other.getClass.getSimpleName))

final case class ErrorResponse(error: String, message: String) derives JsonCodec
