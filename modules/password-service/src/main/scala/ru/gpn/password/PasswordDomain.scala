package ru.gpn.password

import ru.gpn.common.JsonSupport.given
import zio.json.*

import java.util.UUID

final case class PasswordRecord(
    id: UUID,
    name: String,
    password: String,
    comment: String,
    created: Long,
    deleted: Option[Long]
) derives JsonCodec

final case class CreatePasswordRequest(
    name: String,
    password: String,
    comment: Option[String] = None
) derives JsonCodec

final case class UpdatePasswordRequest(
    name: Option[String] = None,
    password: Option[String] = None,
    comment: Option[String] = None
) derives JsonCodec

final case class PasswordHistoryEntry(
    id: Long,
    password: String,
    changed: Long
) derives JsonCodec

final case class OldPasswordRecord(
    id: UUID,
    name: String,
    created: Long,
    lastChanged: Long,
    ageDays: Long
) derives JsonCodec

final case class PasswordStats(
    total: Long,
    active: Long,
    deleted: Long,
    uniquePasswords: Long,
    duplicatePasswordGroups: Long,
    duplicatePasswordEntries: Long,
    oldPasswords: List[OldPasswordRecord]
) derives JsonCodec

final case class CsvImportResult(
    imported: Int,
    failed: Int,
    errors: List[String]
) derives JsonCodec

final case class HealthResponse(status: String) derives JsonCodec

final case class NewPassword(
    name: String,
    password: String,
    comment: String
)

final case class PasswordPatch(
    name: Option[String],
    password: Option[String],
    comment: Option[String]
)

enum PasswordSearchField:
  case Name, Password, Comment, AnyField

object PasswordSearchField:
  def parse(value: Option[String]): Either[String, PasswordSearchField] =
    value.map(_.trim.toLowerCase) match
      case None | Some("")        => Right(AnyField)
      case Some("name")           => Right(Name)
      case Some("password")       => Right(Password)
      case Some("comment")        => Right(Comment)
      case Some("any")            => Right(AnyField)
      case Some(other)            => Left(s"Unsupported search field: $other")

enum PasswordSearchMode:
  case Exact, Partial

object PasswordSearchMode:
  def parse(value: Option[String]): Either[String, PasswordSearchMode] =
    value.map(_.trim.toLowerCase) match
      case None | Some("")      => Right(Partial)
      case Some("exact")        => Right(Exact)
      case Some("partial")      => Right(Partial)
      case Some(other)          => Left(s"Unsupported search mode: $other")
