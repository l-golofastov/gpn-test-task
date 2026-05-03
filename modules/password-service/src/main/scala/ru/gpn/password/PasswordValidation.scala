package ru.gpn.password

import ru.gpn.common.AppError
import zio.*

object PasswordValidation:
  private val MaxNameLength = 255
  private val MaxPasswordLength = 4096
  private val MaxCommentLength = 4096

  def create(request: CreatePasswordRequest): IO[AppError.Validation, NewPassword] =
    ZIO.fromEither(createEither(request.name, request.password, request.comment))
      .mapError(AppError.Validation.apply)

  def patch(request: UpdatePasswordRequest): IO[AppError.Validation, PasswordPatch] =
    ZIO
      .fromEither {
        for
          name <- request.name match
            case Some(value) => validateName(value).map(Some(_))
            case None        => Right(None)
          password <- request.password match
            case Some(value) => validatePassword(value).map(Some(_))
            case None        => Right(None)
          comment <- request.comment match
            case Some(value) => validateComment(Some(value)).map(Some(_))
            case None        => Right(None)
          patch = PasswordPatch(name, password, comment)
          _ <-
            if patch.name.isEmpty && patch.password.isEmpty && patch.comment.isEmpty then
              Left("At least one field must be provided")
            else Right(())
        yield patch
      }
      .mapError(AppError.Validation.apply)

  def createEither(name: String, password: String, comment: Option[String]): Either[String, NewPassword] =
    for
      validName <- validateName(name)
      validPassword <- validatePassword(password)
      validComment <- validateComment(comment)
    yield NewPassword(validName, validPassword, validComment)

  private def validateName(value: String): Either[String, String] =
    val trimmed = value.trim
    if trimmed.isEmpty then Left("Name must not be empty")
    else if trimmed.length > MaxNameLength then Left(s"Name must be at most $MaxNameLength characters")
    else Right(trimmed)

  private def validatePassword(value: String): Either[String, String] =
    if value.isEmpty then Left("Password must not be empty")
    else if value.length > MaxPasswordLength then Left(s"Password must be at most $MaxPasswordLength characters")
    else Right(value)

  private def validateComment(value: Option[String]): Either[String, String] =
    val normalized = value.getOrElse("").trim
    if normalized.length > MaxCommentLength then Left(s"Comment must be at most $MaxCommentLength characters")
    else Right(normalized)
