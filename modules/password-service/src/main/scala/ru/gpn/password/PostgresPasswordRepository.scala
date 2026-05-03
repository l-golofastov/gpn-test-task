package ru.gpn.password

import ru.gpn.common.AppError
import ru.gpn.common.db.{Database, DbConfig}
import zio.*

import java.sql.{Connection, PreparedStatement, ResultSet}
import java.util.UUID

final case class PostgresPasswordRepository(database: Database) extends PasswordRepository:
  override def create(input: NewPassword, now: Long): IO[AppError, PasswordRecord] =
    database
      .transaction { connection =>
        val id = UUID.randomUUID()
        executeUpdate(
          connection,
          """
            INSERT INTO password_entries (id, name, password, comment_text, created_at, deleted_at)
            VALUES (?, ?, ?, ?, ?, NULL)
          """,
          List(id, input.name, input.password, input.comment, now)
        )
        insertHistory(connection, id, input.password, now)
        PasswordRecord(id, input.name, input.password, input.comment, now, None)
      }
      .mapError(DbConfig.mapError)

  override def get(id: UUID, includeDeleted: Boolean): IO[AppError, PasswordRecord] =
    database
      .read(connection => selectById(connection, id, forUpdate = false))
      .mapError(DbConfig.mapError)
      .flatMap {
        case Some(record) if includeDeleted || record.deleted.isEmpty => ZIO.succeed(record)
        case _ => ZIO.fail(AppError.NotFound(s"Password record $id was not found"))
      }

  override def list(includeDeleted: Boolean, limit: Int, offset: Int): IO[AppError, List[PasswordRecord]] =
    val where = if includeDeleted then "" else "WHERE deleted_at IS NULL"
    database
      .read { connection =>
        queryRecords(
          connection,
          s"""
            SELECT id, name, password, comment_text, created_at, deleted_at
            FROM password_entries
            $where
            ORDER BY created_at DESC, id
            LIMIT ? OFFSET ?
          """,
          List(limit, offset)
        )
      }
      .mapError(DbConfig.mapError)

  override def search(
      query: String,
      field: PasswordSearchField,
      mode: PasswordSearchMode,
      includeDeleted: Boolean,
      limit: Int,
      offset: Int
  ): IO[AppError, List[PasswordRecord]] =
    val (condition, conditionParams) = searchCondition(query, field, mode)
    val where =
      if includeDeleted then s"WHERE $condition"
      else s"WHERE deleted_at IS NULL AND $condition"

    database
      .read { connection =>
        queryRecords(
          connection,
          s"""
            SELECT id, name, password, comment_text, created_at, deleted_at
            FROM password_entries
            $where
            ORDER BY created_at DESC, id
            LIMIT ? OFFSET ?
          """,
          conditionParams ++ List(limit, offset)
        )
      }
      .mapError(DbConfig.mapError)

  override def update(id: UUID, patch: PasswordPatch, now: Long): IO[AppError, PasswordRecord] =
    database
      .transaction { connection =>
        selectById(connection, id, forUpdate = true) match
          case None =>
            Left(AppError.NotFound(s"Password record $id was not found"))
          case Some(existing) if existing.deleted.nonEmpty =>
            Left(AppError.NotFound(s"Password record $id was not found"))
          case Some(existing) =>
            val updated = existing.copy(
              name = patch.name.getOrElse(existing.name),
              password = patch.password.getOrElse(existing.password),
              comment = patch.comment.getOrElse(existing.comment)
            )

            executeUpdate(
              connection,
              """
                UPDATE password_entries
                SET name = ?, password = ?, comment_text = ?
                WHERE id = ?
              """,
              List(updated.name, updated.password, updated.comment, id)
            )

            if updated.password != existing.password then
              insertHistory(connection, id, updated.password, now)

            Right(updated)
      }
      .mapError(DbConfig.mapError)
      .flatMap(ZIO.fromEither(_))

  override def delete(id: UUID, now: Long): IO[AppError, Unit] =
    database
      .transaction { connection =>
        selectById(connection, id, forUpdate = true) match
          case None =>
            Left(AppError.NotFound(s"Password record $id was not found"))
          case Some(existing) if existing.deleted.nonEmpty =>
            Left(AppError.NotFound(s"Password record $id was not found"))
          case Some(_) =>
            executeUpdate(
              connection,
              "UPDATE password_entries SET deleted_at = ? WHERE id = ?",
              List(now, id)
            )
            Right(())
      }
      .mapError(DbConfig.mapError)
      .flatMap(ZIO.fromEither(_))

  override def history(id: UUID): IO[AppError, List[PasswordHistoryEntry]] =
    database
      .read { connection =>
        selectById(connection, id, forUpdate = false) match
          case None => Left(AppError.NotFound(s"Password record $id was not found"))
          case Some(_) =>
            Right(
              queryHistory(
                connection,
                """
                  SELECT id, password, changed_at
                  FROM password_history
                  WHERE entry_id = ?
                  ORDER BY changed_at ASC, id ASC
                """,
                List(id)
              )
            )
      }
      .mapError(DbConfig.mapError)
      .flatMap(ZIO.fromEither(_))

  override def stats(now: Long, olderThanDays: Int, oldestLimit: Int): IO[AppError, PasswordStats] =
    database
      .read { connection =>
        val threshold = now - olderThanDays.toLong * 86400L
        val total = queryLong(connection, "SELECT COUNT(*) FROM password_entries", Nil)
        val active = queryLong(connection, "SELECT COUNT(*) FROM password_entries WHERE deleted_at IS NULL", Nil)
        val deleted = total - active
        val uniquePasswords = queryLong(
          connection,
          """
            SELECT COUNT(*)
            FROM (
              SELECT password
              FROM password_entries
              WHERE deleted_at IS NULL
              GROUP BY password
              HAVING COUNT(*) = 1
            ) unique_passwords
          """,
          Nil
        )
        val duplicateGroups = queryLong(
          connection,
          """
            SELECT COUNT(*)
            FROM (
              SELECT password
              FROM password_entries
              WHERE deleted_at IS NULL
              GROUP BY password
              HAVING COUNT(*) > 1
            ) duplicate_passwords
          """,
          Nil
        )
        val duplicateEntries = queryLong(
          connection,
          """
            SELECT COALESCE(SUM(password_count), 0)
            FROM (
              SELECT COUNT(*) AS password_count
              FROM password_entries
              WHERE deleted_at IS NULL
              GROUP BY password
              HAVING COUNT(*) > 1
            ) duplicate_password_entries
          """,
          Nil
        )
        val oldPasswords = queryOldPasswords(connection, threshold, now, oldestLimit)

        PasswordStats(
          total = total,
          active = active,
          deleted = deleted,
          uniquePasswords = uniquePasswords,
          duplicatePasswordGroups = duplicateGroups,
          duplicatePasswordEntries = duplicateEntries,
          oldPasswords = oldPasswords
        )
      }
      .mapError(DbConfig.mapError)

  private def selectById(connection: Connection, id: UUID, forUpdate: Boolean): Option[PasswordRecord] =
    val lock = if forUpdate then " FOR UPDATE" else ""
    queryRecords(
      connection,
      s"""
        SELECT id, name, password, comment_text, created_at, deleted_at
        FROM password_entries
        WHERE id = ?
        $lock
      """,
      List(id)
    ).headOption

  private def queryRecords(connection: Connection, sql: String, params: List[Any]): List[PasswordRecord] =
    withStatement(connection, sql, params) { statement =>
      val resultSet = statement.executeQuery()
      try
        Iterator
          .continually(resultSet.next())
          .takeWhile(identity)
          .map(_ => readRecord(resultSet))
          .toList
      finally resultSet.close()
    }

  private def queryHistory(connection: Connection, sql: String, params: List[Any]): List[PasswordHistoryEntry] =
    withStatement(connection, sql, params) { statement =>
      val resultSet = statement.executeQuery()
      try
        Iterator
          .continually(resultSet.next())
          .takeWhile(identity)
          .map { _ =>
            PasswordHistoryEntry(
              id = resultSet.getLong("id"),
              password = resultSet.getString("password"),
              changed = resultSet.getLong("changed_at")
            )
          }
          .toList
      finally resultSet.close()
    }

  private def queryLong(connection: Connection, sql: String, params: List[Any]): Long =
    withStatement(connection, sql, params) { statement =>
      val resultSet = statement.executeQuery()
      try
        if resultSet.next() then resultSet.getLong(1)
        else 0L
      finally resultSet.close()
    }

  private def queryOldPasswords(
      connection: Connection,
      threshold: Long,
      now: Long,
      limit: Int
  ): List[OldPasswordRecord] =
    withStatement(
      connection,
      """
        SELECT
          entry.id,
          entry.name,
          entry.created_at,
          COALESCE(MAX(history.changed_at), entry.created_at) AS last_changed_at
        FROM password_entries entry
        LEFT JOIN password_history history ON history.entry_id = entry.id
        WHERE entry.deleted_at IS NULL
        GROUP BY entry.id, entry.name, entry.created_at
        HAVING COALESCE(MAX(history.changed_at), entry.created_at) <= ?
        ORDER BY last_changed_at ASC, entry.id
        LIMIT ?
      """,
      List(threshold, limit)
    ) { statement =>
      val resultSet = statement.executeQuery()
      try
        Iterator
          .continually(resultSet.next())
          .takeWhile(identity)
          .map { _ =>
            val created = resultSet.getLong("created_at")
            val lastChanged = resultSet.getLong("last_changed_at")
            OldPasswordRecord(
              id = resultSet.getObject("id", classOf[UUID]),
              name = resultSet.getString("name"),
              created = created,
              lastChanged = lastChanged,
              ageDays = math.max(0L, (now - lastChanged) / 86400L)
            )
          }
          .toList
      finally resultSet.close()
    }

  private def insertHistory(connection: Connection, entryId: UUID, password: String, changedAt: Long): Long =
    withStatement(
      connection,
      """
        INSERT INTO password_history (entry_id, password, changed_at)
        VALUES (?, ?, ?)
        RETURNING id
      """,
      List(entryId, password, changedAt)
    ) { statement =>
      val resultSet = statement.executeQuery()
      try
        resultSet.next()
        resultSet.getLong("id")
      finally resultSet.close()
    }

  private def executeUpdate(connection: Connection, sql: String, params: List[Any]): Int =
    withStatement(connection, sql, params)(_.executeUpdate())

  private def readRecord(resultSet: ResultSet): PasswordRecord =
    PasswordRecord(
      id = resultSet.getObject("id", classOf[UUID]),
      name = resultSet.getString("name"),
      password = resultSet.getString("password"),
      comment = resultSet.getString("comment_text"),
      created = resultSet.getLong("created_at"),
      deleted = nullableLong(resultSet, "deleted_at")
    )

  private def nullableLong(resultSet: ResultSet, column: String): Option[Long] =
    val value = resultSet.getLong(column)
    if resultSet.wasNull() then None else Some(value)

  private def searchCondition(
      query: String,
      field: PasswordSearchField,
      mode: PasswordSearchMode
  ): (String, List[Any]) =
    val columns = field match
      case PasswordSearchField.Name     => List("name")
      case PasswordSearchField.Password => List("password")
      case PasswordSearchField.Comment  => List("comment_text")
      case PasswordSearchField.AnyField => List("name", "password", "comment_text")

    mode match
      case PasswordSearchMode.Exact =>
        columns.map(column => s"$column = ?").mkString("(", " OR ", ")") -> List.fill(columns.size)(query)
      case PasswordSearchMode.Partial =>
        val pattern = s"%${escapeLike(query)}%"
        columns.map(column => s"LOWER($column) LIKE LOWER(?) ESCAPE '\\'").mkString("(", " OR ", ")") ->
          List.fill(columns.size)(pattern)

  private def escapeLike(value: String): String =
    value.flatMap {
      case '%'  => "\\%"
      case '_'  => "\\_"
      case '\\' => "\\\\"
      case char => char.toString
    }

  private def withStatement[A](connection: Connection, sql: String, params: List[Any])(
      use: PreparedStatement => A
  ): A =
    val statement = connection.prepareStatement(sql)
    try
      params.zipWithIndex.foreach { case (value, index) =>
        setParam(statement, index + 1, value)
      }
      use(statement)
    finally statement.close()

  private def setParam(statement: PreparedStatement, index: Int, value: Any): Unit =
    value match
      case uuid: UUID     => statement.setObject(index, uuid)
      case text: String   => statement.setString(index, text)
      case int: Int       => statement.setInt(index, int)
      case long: Long     => statement.setLong(index, long)
      case other          => statement.setObject(index, other)
