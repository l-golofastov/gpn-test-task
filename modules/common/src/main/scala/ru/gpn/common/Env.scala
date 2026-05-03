package ru.gpn.common

import zio.*

final case class Env(values: Map[String, String]):
  def string(name: String, default: String): String =
    values.getOrElse(name, default)

  def optionalString(name: String): Option[String] =
    values.get(name).filter(_.nonEmpty)

  def int(name: String, default: Int): IO[AppError.Config, Int] =
    values.get(name) match
      case None => ZIO.succeed(default)
      case Some(value) =>
        ZIO
          .attempt(value.toInt)
          .mapError(_ => AppError.Config(s"Environment variable $name must be an integer"))

  def positiveInt(name: String, default: Int): IO[AppError.Config, Int] =
    int(name, default).flatMap { value =>
      ZIO.fail(AppError.Config(s"Environment variable $name must be positive")).when(value <= 0).as(value)
    }

  def long(name: String, default: Long): IO[AppError.Config, Long] =
    values.get(name) match
      case None => ZIO.succeed(default)
      case Some(value) =>
        ZIO
          .attempt(value.toLong)
          .mapError(_ => AppError.Config(s"Environment variable $name must be a long integer"))

  def positiveLong(name: String, default: Long): IO[AppError.Config, Long] =
    long(name, default).flatMap { value =>
      ZIO.fail(AppError.Config(s"Environment variable $name must be positive")).when(value <= 0L).as(value)
    }

object Env:
  def live: UIO[Env] =
    ZIO.succeed(Env(_root_.java.lang.System.getenv().entrySet().toArray.toList.collect {
      case entry: java.util.Map.Entry[?, ?] => entry.getKey.toString -> entry.getValue.toString
    }.toMap))
