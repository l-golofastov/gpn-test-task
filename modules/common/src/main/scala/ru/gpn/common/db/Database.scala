package ru.gpn.common.db

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import ru.gpn.common.AppError
import zio.*

import java.sql.Connection

final case class DbConfig(
    jdbcUrl: String,
    username: String,
    password: String,
    driverClassName: String,
    maximumPoolSize: Int
)

trait Database:
  def read[A](operation: Connection => A): Task[A]
  def transaction[A](operation: Connection => A): Task[A]

object Database:
  def read[A](operation: Connection => A): ZIO[Database, Throwable, A] =
    ZIO.serviceWithZIO[Database](_.read(operation))

  def transaction[A](operation: Connection => A): ZIO[Database, Throwable, A] =
    ZIO.serviceWithZIO[Database](_.transaction(operation))

final case class HikariDatabase(dataSource: HikariDataSource) extends Database:
  override def read[A](operation: Connection => A): Task[A] =
    ZIO.attemptBlocking {
      val connection = dataSource.getConnection
      try operation(connection)
      finally connection.close()
    }

  override def transaction[A](operation: Connection => A): Task[A] =
    ZIO.attemptBlocking {
      val connection = dataSource.getConnection
      val previousAutoCommit = connection.getAutoCommit
      connection.setAutoCommit(false)
      try
        val result = operation(connection)
        connection.commit()
        result
      catch
        case error: Throwable =>
          connection.rollback()
          throw error
      finally
        connection.setAutoCommit(previousAutoCommit)
        connection.close()
    }

object HikariDatabase:
  def layer(config: DbConfig): ZLayer[Any, Throwable, Database] =
    ZLayer.scoped {
      ZIO.acquireRelease {
        ZIO.attempt {
          Class.forName(config.driverClassName)
          val hikari = new HikariConfig()
          hikari.setJdbcUrl(config.jdbcUrl)
          hikari.setUsername(config.username)
          hikari.setPassword(config.password)
          hikari.setDriverClassName(config.driverClassName)
          hikari.setMaximumPoolSize(config.maximumPoolSize)
          HikariDatabase(new HikariDataSource(hikari))
        }
      }(database => ZIO.succeed(database.dataSource.close()))
    }

object DbConfig:
  def postgres(
      jdbcUrl: String,
      username: String,
      password: String,
      maximumPoolSize: Int = 8
  ): DbConfig =
    DbConfig(
      jdbcUrl = jdbcUrl,
      username = username,
      password = password,
      driverClassName = "org.postgresql.Driver",
      maximumPoolSize = maximumPoolSize
    )

  def mapError(error: Throwable): AppError.Database =
    AppError.Database(Option(error.getMessage).getOrElse(error.getClass.getSimpleName))
