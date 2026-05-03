package ru.gpn.password

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import ru.gpn.common.db.{Database, DbConfig, HikariDatabase, Migrations}
import zio.*
import zio.test.*

object PostgresPasswordRepositorySpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment, Any] =
    suite("PostgresPasswordRepository")(
      test("persists CRUD, history, search and stats when PASSWORD_SERVICE_IT=1") {
        if sys.env.get("PASSWORD_SERVICE_IT").contains("1") then postgresScenario
        else ZIO.succeed(assertTrue(true))
      }
    )

  private def postgresScenario: ZIO[Any, Throwable, TestResult] =
    ZIO.scoped {
      for
        container <- ZIO.acquireRelease(startPostgres)(container => ZIO.attempt(container.stop()).orDie)
        dbConfig = DbConfig.postgres(
          jdbcUrl = container.getJdbcUrl,
          username = container.getUsername,
          password = container.getPassword,
          maximumPoolSize = 2
        )
        result <- repositoryScenario.provideSomeLayer[Scope](HikariDatabase.layer(dbConfig))
      yield result
    }

  private def repositoryScenario: ZIO[Database, Throwable, TestResult] =
    for
      _ <- Migrations.runResource("db/password-service.sql")
      database <- ZIO.service[Database]
      repository = PostgresPasswordRepository(database)
      created <- repository.create(NewPassword("mail", "secret", "primary"), 100L)
      updated <- repository.update(created.id, PasswordPatch(None, Some("rotated"), None), 200L)
      duplicateA <- repository.create(NewPassword("duplicate-a", "same", ""), 300L)
      duplicateB <- repository.create(NewPassword("duplicate-b", "same", ""), 301L)
      search <- repository.search("rotated", PasswordSearchField.Password, PasswordSearchMode.Exact, includeDeleted = false, limit = 10, offset = 0)
      history <- repository.history(created.id)
      stats <- repository.stats(now = 400L * 86400L, olderThanDays = 365, oldestLimit = 10)
      _ <- repository.delete(created.id, 500L)
      missing <- repository.get(created.id, includeDeleted = false).exit
      deleted <- repository.get(created.id, includeDeleted = true)
    yield assertTrue(
      updated.password == "rotated",
      search.map(_.id) == List(created.id),
      history.map(_.password) == List("secret", "rotated"),
      stats.active == 3L,
      stats.uniquePasswords == 1L,
      stats.duplicatePasswordGroups == 1L,
      stats.duplicatePasswordEntries == 2L,
      stats.oldPasswords.map(_.id).contains(duplicateA.id),
      stats.oldPasswords.map(_.id).contains(duplicateB.id),
      missing.isFailure,
      deleted.deleted.contains(500L)
    )

  private def startPostgres: Task[TestPostgresContainer] =
    ZIO.attempt {
      val container = TestPostgresContainer("postgres:16-alpine")
      container.withDatabaseName("passwords")
      container.withUsername("postgres")
      container.withPassword("postgres")
      container.start()
      container
    }

final class TestPostgresContainer(imageName: String)
    extends PostgreSQLContainer[TestPostgresContainer](DockerImageName.parse(imageName))
