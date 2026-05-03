package ru.gpn.password

import ru.gpn.common.Env
import ru.gpn.common.db.{Database, DbConfig, HikariDatabase, Migrations}
import ru.gpn.common.http.{ServerConfig, SimpleHttpServer}
import zio.*

object PasswordServiceApp extends ZIOAppDefault:
  override def run: ZIO[Any, Any, Any] =
    (for
      env <- Env.live
      host = readString(env, List("PASSWORD_HTTP_HOST", "PASSWORD_SERVICE_HOST"), "0.0.0.0")
      port <- readPositiveInt(env, List("PASSWORD_HTTP_PORT", "PASSWORD_SERVICE_PORT"), 8081)
      dbConfig <- readDbConfig(env)
      _ <- ZIO.scoped {
        val server =
          for
            _ <- Migrations.runResource("db/password-service.sql")
            database <- ZIO.service[Database]
            repository = PostgresPasswordRepository(database)
            _ <- SimpleHttpServer.serve(ServerConfig(host, port), PasswordRoutes.router(repository))
          yield ()

        server.provideSomeLayer[Scope](HikariDatabase.layer(dbConfig))
      }
    yield ()).tapError(error => ZIO.logError(error.toString))

  private def readDbConfig(env: Env): IO[ru.gpn.common.AppError.Config, DbConfig] =
    for
      poolSize <- readPositiveInt(env, List("DB_POOL_SIZE", "PASSWORD_DB_POOL_SIZE"), 8)
    yield DbConfig.postgres(
      jdbcUrl = readString(env, List("DB_JDBC_URL", "PASSWORD_DB_URL"), "jdbc:postgresql://localhost:5432/gpn"),
      username = readString(env, List("DB_USER", "PASSWORD_DB_USER"), "gpn"),
      password = readString(env, List("DB_PASSWORD", "PASSWORD_DB_PASSWORD"), "gpn"),
      maximumPoolSize = poolSize
    )

  private def readString(env: Env, names: List[String], default: String): String =
    names.flatMap(env.optionalString).headOption.getOrElse(default)

  private def readPositiveInt(
      env: Env,
      names: List[String],
      default: Int
  ): IO[ru.gpn.common.AppError.Config, Int] =
    names.flatMap(env.optionalString).headOption match
      case None => ZIO.succeed(default)
      case Some(value) =>
        ZIO
          .attempt(value.toInt)
          .mapError(_ => ru.gpn.common.AppError.Config(s"Environment variable ${names.mkString("/")} must be an integer"))
          .flatMap(parsed =>
            ZIO.fail(ru.gpn.common.AppError.Config(s"Environment variable ${names.mkString("/")} must be positive")).when(parsed <= 0).as(parsed)
          )
