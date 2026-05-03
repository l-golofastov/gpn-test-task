package ru.gpn.common.db

import ru.gpn.common.AppError
import zio.*

import java.nio.charset.StandardCharsets
import java.sql.Connection

object Migrations:
  def runResource(resourceName: String): ZIO[Database, AppError, Unit] =
    for
      sql <- loadResource(resourceName)
      _ <- Database
        .transaction { connection =>
          splitStatements(sql).foreach { statementSql =>
            val statement = connection.createStatement()
            try statement.execute(statementSql)
            finally statement.close()
          }
        }
        .mapError(error => AppError.Database(s"Migration $resourceName failed: ${error.getMessage}"))
    yield ()

  private def loadResource(resourceName: String): IO[AppError.FileSystem, String] =
    ZIO.attempt {
      val normalized = resourceName.stripPrefix("/")
      val stream = Thread.currentThread().getContextClassLoader.getResourceAsStream(normalized)
      if stream == null then throw new IllegalArgumentException(s"Resource not found: $resourceName")
      try String(stream.readAllBytes(), StandardCharsets.UTF_8)
      finally stream.close()
    }.mapError(error => AppError.FileSystem(error.getMessage))

  private def splitStatements(sql: String): List[String] =
    sql.linesIterator
      .map(_.trim)
      .filterNot(line => line.startsWith("--"))
      .mkString("\n")
      .split(";")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
