package ru.gpn.password

import ru.gpn.common.AppError
import zio.*

import java.util.UUID

trait PasswordRepository:
  def create(input: NewPassword, now: Long): IO[AppError, PasswordRecord]

  def get(id: UUID, includeDeleted: Boolean): IO[AppError, PasswordRecord]

  def list(includeDeleted: Boolean, limit: Int, offset: Int): IO[AppError, List[PasswordRecord]]

  def search(
      query: String,
      field: PasswordSearchField,
      mode: PasswordSearchMode,
      includeDeleted: Boolean,
      limit: Int,
      offset: Int
  ): IO[AppError, List[PasswordRecord]]

  def update(id: UUID, patch: PasswordPatch, now: Long): IO[AppError, PasswordRecord]

  def delete(id: UUID, now: Long): IO[AppError, Unit]

  def history(id: UUID): IO[AppError, List[PasswordHistoryEntry]]

  def stats(now: Long, olderThanDays: Int, oldestLimit: Int): IO[AppError, PasswordStats]
