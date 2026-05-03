package ru.gpn.password

import ru.gpn.common.AppError
import zio.*

import java.nio.charset.StandardCharsets
import java.util.UUID

final class InMemoryPasswordRepository private (ref: Ref[InMemoryPasswordRepository.State]) extends PasswordRepository:
  import InMemoryPasswordRepository.State

  override def create(input: NewPassword, now: Long): IO[AppError, PasswordRecord] =
    ref.modify { state =>
      val id = UUID.nameUUIDFromBytes(s"${state.nextRecord}:${input.name}".getBytes(StandardCharsets.UTF_8))
      val record = PasswordRecord(id, input.name, input.password, input.comment, now, None)
      val history = PasswordHistoryEntry(state.nextHistory, input.password, now)
      val updated = state.copy(
        nextRecord = state.nextRecord + 1,
        nextHistory = state.nextHistory + 1,
        records = state.records.updated(id, record),
        history = state.history.updated(id, state.history.getOrElse(id, Nil) :+ history)
      )
      record -> updated
    }

  override def get(id: UUID, includeDeleted: Boolean): IO[AppError, PasswordRecord] =
    ref.get.flatMap { state =>
      state.records.get(id) match
        case Some(record) if includeDeleted || record.deleted.isEmpty => ZIO.succeed(record)
        case _ => ZIO.fail(AppError.NotFound(s"Password record $id was not found"))
    }

  override def list(includeDeleted: Boolean, limit: Int, offset: Int): IO[AppError, List[PasswordRecord]] =
    ref.get.map { state =>
      state.records.values.toList
        .filter(record => includeDeleted || record.deleted.isEmpty)
        .sortBy(record => (-record.created, record.id.toString))
        .slice(offset, offset + limit)
    }

  override def search(
      query: String,
      field: PasswordSearchField,
      mode: PasswordSearchMode,
      includeDeleted: Boolean,
      limit: Int,
      offset: Int
  ): IO[AppError, List[PasswordRecord]] =
    list(includeDeleted, Int.MaxValue, 0).map { records =>
      records
        .filter(matches(_, query, field, mode))
        .slice(offset, offset + limit)
    }

  override def update(id: UUID, patch: PasswordPatch, now: Long): IO[AppError, PasswordRecord] =
    ref.modify { state =>
      state.records.get(id) match
        case Some(existing) if existing.deleted.isEmpty =>
          val updated = existing.copy(
            name = patch.name.getOrElse(existing.name),
            password = patch.password.getOrElse(existing.password),
            comment = patch.comment.getOrElse(existing.comment)
          )
          val history =
            if updated.password == existing.password then state.history
            else
              state.history.updated(
                id,
                state.history.getOrElse(id, Nil) :+ PasswordHistoryEntry(state.nextHistory, updated.password, now)
              )
          val nextHistory =
            if updated.password == existing.password then state.nextHistory
            else state.nextHistory + 1

          ZIO.succeed(updated) -> state.copy(
            nextHistory = nextHistory,
            records = state.records.updated(id, updated),
            history = history
          )
        case _ =>
          ZIO.fail(AppError.NotFound(s"Password record $id was not found")) -> state
    }.flatten

  override def delete(id: UUID, now: Long): IO[AppError, Unit] =
    ref.modify { state =>
      state.records.get(id) match
        case Some(existing) if existing.deleted.isEmpty =>
          ZIO.unit -> state.copy(records = state.records.updated(id, existing.copy(deleted = Some(now))))
        case _ =>
          ZIO.fail(AppError.NotFound(s"Password record $id was not found")) -> state
    }.flatten

  override def history(id: UUID): IO[AppError, List[PasswordHistoryEntry]] =
    ref.get.flatMap { state =>
      if state.records.contains(id) then ZIO.succeed(state.history.getOrElse(id, Nil))
      else ZIO.fail(AppError.NotFound(s"Password record $id was not found"))
    }

  override def stats(now: Long, olderThanDays: Int, oldestLimit: Int): IO[AppError, PasswordStats] =
    ref.get.map { state =>
      val all = state.records.values.toList
      val active = all.filter(_.deleted.isEmpty)
      val duplicateGroups = active.groupBy(_.password).values.filter(_.size > 1).toList
      val threshold = now - olderThanDays.toLong * 86400L
      val oldPasswords = active
        .map { record =>
          val lastChanged = state.history.getOrElse(record.id, Nil).map(_.changed).maxOption.getOrElse(record.created)
          record -> lastChanged
        }
        .filter { case (_, lastChanged) => lastChanged <= threshold }
        .sortBy { case (record, lastChanged) => (lastChanged, record.id.toString) }
        .take(oldestLimit)
        .map { case (record, lastChanged) =>
          OldPasswordRecord(
            record.id,
            record.name,
            record.created,
            lastChanged,
            math.max(0L, (now - lastChanged) / 86400L)
          )
        }

      PasswordStats(
        total = all.size.toLong,
        active = active.size.toLong,
        deleted = all.count(_.deleted.nonEmpty).toLong,
        uniquePasswords = active.groupBy(_.password).values.count(_.size == 1).toLong,
        duplicatePasswordGroups = duplicateGroups.size.toLong,
        duplicatePasswordEntries = duplicateGroups.map(_.size.toLong).sum,
        oldPasswords = oldPasswords
      )
    }

  private def matches(
      record: PasswordRecord,
      query: String,
      field: PasswordSearchField,
      mode: PasswordSearchMode
  ): Boolean =
    val values = field match
      case PasswordSearchField.Name     => List(record.name)
      case PasswordSearchField.Password => List(record.password)
      case PasswordSearchField.Comment  => List(record.comment)
      case PasswordSearchField.AnyField => List(record.name, record.password, record.comment)

    mode match
      case PasswordSearchMode.Exact =>
        values.exists(_ == query)
      case PasswordSearchMode.Partial =>
        values.exists(_.toLowerCase.contains(query.toLowerCase))

object InMemoryPasswordRepository:
  final case class State(
      nextRecord: Long,
      nextHistory: Long,
      records: Map[UUID, PasswordRecord],
      history: Map[UUID, List[PasswordHistoryEntry]]
  )

  def make: UIO[InMemoryPasswordRepository] =
    Ref
      .make(State(nextRecord = 1L, nextHistory = 1L, records = Map.empty, history = Map.empty))
      .map(new InMemoryPasswordRepository(_))
