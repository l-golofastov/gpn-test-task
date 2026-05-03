package ru.gpn.scheduler

import org.testcontainers.containers.PostgreSQLContainer
import ru.gpn.common.db.{Database, DbConfig, HikariDatabase, Migrations}
import zio.*
import zio.test.*

import java.util.UUID

object JdbcWasteRepositorySpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("JdbcWasteRepository")(
      test("persists records and snapshots in PostgreSQL when SCHEDULER_RUN_IT=1") {
        if sys.env.get("SCHEDULER_RUN_IT").contains("1") then postgresRoundTrip
        else ZIO.succeed(assertTrue(true))
      }
    )

  private def postgresRoundTrip: ZIO[Any, Throwable, TestResult] =
    ZIO.scoped {
      for
        container <- ZIO.acquireRelease(startContainer)(container => ZIO.attempt(container.stop()).orDie)
        result <- repositoryRoundTrip(container)
      yield result
    }

  private def startContainer: Task[SchedulerPostgresContainer] =
    ZIO.attempt {
      val container = SchedulerPostgresContainer()
      container.start()
      container
    }

  private def repositoryRoundTrip(container: SchedulerPostgresContainer): ZIO[Any, Throwable, TestResult] =
    val dbConfig =
      DbConfig.postgres(container.getJdbcUrl, container.getUsername, container.getPassword)

    val program =
      for
        _ <- Migrations.runResource("db/scheduler-service.sql")
        database <- ZIO.service[Database]
        repository = JdbcWasteRepository(database)
        now <- Clock.instant
        inserted <- repository.insertAll(
          WasteType.Plastic,
          Chunk(
            WasteEntry(UUID.randomUUID(), WasteType.Plastic, BigDecimal("1.250"), now, Map("test" -> "true")),
            WasteEntry(UUID.randomUUID(), WasteType.Plastic, BigDecimal("1.750"), now, Map("test" -> "true"))
          )
        )
        totals <- repository.totalWeights
        snapshot = WasteSnapshot(UUID.randomUUID(), now, WasteTotals.fromMap(totals))
        _ <- repository.saveSnapshot(snapshot)
        latest <- repository.latestSnapshot
      yield assertTrue(
        inserted == 2,
        totals.get(WasteType.Plastic).contains(BigDecimal("3.000")),
        totals.get(WasteType.Glass).contains(BigDecimal(0)),
        latest.exists(_.totals.plastic == BigDecimal("3.000")),
        latest.exists(_.totals.total == BigDecimal("3.000"))
      )

    program.provideLayer(HikariDatabase.layer(dbConfig))

private final class SchedulerPostgresContainer
    extends PostgreSQLContainer[SchedulerPostgresContainer]("postgres:16-alpine")
