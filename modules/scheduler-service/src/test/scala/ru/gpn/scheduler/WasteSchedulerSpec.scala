package ru.gpn.scheduler

import ru.gpn.common.db.DbConfig
import ru.gpn.common.http.ServerConfig
import zio.*
import zio.test.*

import java.util.UUID

object WasteSchedulerSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("WasteScheduler")(
      test("manual generate tick inserts configured count for each type") {
        for
          repository <- InMemoryWasteRepository.make
          scheduler <- WasteScheduler.make(repository, testConfig(itemsPerType = 2))
          result <- scheduler.generateTick
          counts <- repository.tableCounts
          state <- scheduler.state
        yield assertTrue(
          result.totalInserted == WasteType.all.size * 2,
          WasteType.all.forall(wasteType => result.insertedByType.get(wasteType.id).contains(2)),
          WasteType.all.forall(wasteType => counts.get(wasteType).contains(2L)),
          state.generatedTicks == 1L,
          state.lastGeneratedAt.contains(result.generatedAt)
        )
      },
      test("snapshot tick stores aggregate totals independently from generation") {
        for
          repository <- InMemoryWasteRepository.make
          now <- Clock.instant
          _ <- repository.insertAll(
            WasteType.Plastic,
            Chunk(
              WasteEntry(UUID.randomUUID(), WasteType.Plastic, BigDecimal("1.500"), now, Map.empty),
              WasteEntry(UUID.randomUUID(), WasteType.Plastic, BigDecimal("0.500"), now, Map.empty)
            )
          )
          _ <- repository.insertAll(
            WasteType.Glass,
            Chunk(WasteEntry(UUID.randomUUID(), WasteType.Glass, BigDecimal("2.250"), now, Map.empty))
          )
          scheduler <- WasteScheduler.make(repository, testConfig(itemsPerType = 1))
          snapshot <- scheduler.snapshotTick
          latest <- repository.latestSnapshot
          state <- scheduler.state
        yield assertTrue(
          snapshot.totals.plastic == BigDecimal("2.000"),
          snapshot.totals.glass == BigDecimal("2.250"),
          snapshot.totals.paper == BigDecimal(0),
          snapshot.totals.other == BigDecimal(0),
          snapshot.totals.total == BigDecimal("4.250"),
          latest.contains(snapshot),
          state.snapshotTicks == 1L,
          state.lastSnapshotAt.contains(snapshot.capturedAt)
        )
      }
    )

  private def testConfig(itemsPerType: Int): SchedulerConfig =
    SchedulerConfig(
      http = ServerConfig("127.0.0.1", 0),
      db = DbConfig.postgres("jdbc:postgresql://localhost/scheduler_test", "postgres", "postgres"),
      generateInterval = Duration.fromMillis(10),
      snapshotInterval = Duration.fromMillis(20),
      itemsPerType = itemsPerType,
      statsLimit = 5
    )
