package ru.gpn.password

import ru.gpn.common.Csv

object PasswordCsv:
  private val Headers = List("id", "name", "password", "comment", "created", "deleted")

  final case class ParsedImport(records: List[NewPassword], errors: List[String])

  def render(records: List[PasswordRecord]): String =
    Csv.render(
      Headers,
      records.map { record =>
        List(
          record.id.toString,
          record.name,
          record.password,
          record.comment,
          record.created.toString,
          record.deleted.fold("")(_.toString)
        )
      }
    )

  def parseImport(input: String): Either[String, ParsedImport] =
    Csv.parse(input).map { rows =>
      val parsed = rows.zipWithIndex.map { case (row, index) =>
        val rowNumber = index + 2
        val name = value(row, "name")
        val password = value(row, "password")
        val comment = optionalValue(row, "comment")

        PasswordValidation.createEither(name, password, comment)
          .left
          .map(error => s"Row $rowNumber: $error")
      }

      ParsedImport(
        records = parsed.collect { case Right(record) => record },
        errors = parsed.collect { case Left(error) => error }
      )
    }

  private def value(row: Map[String, String], name: String): String =
    optionalValue(row, name).getOrElse("")

  private def optionalValue(row: Map[String, String], name: String): Option[String] =
    row.collectFirst {
      case (header, value) if header.equalsIgnoreCase(name) => value
    }
