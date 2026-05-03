package ru.gpn.scheduler

import zio.Scope
import zio.test.*

object WasteGeneratorSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("WasteGenerator")(
      test("generates requested count for every waste type") {
        for batch <- WasteGenerator.generateAll(3)
        yield assertTrue(
          batch.entries.keySet == WasteType.all.toSet,
          batch.entries.values.forall(_.size == 3),
          batch.entries.forall { case (wasteType, entries) =>
            entries.forall(entry =>
              entry.wasteType == wasteType &&
                entry.weightKg > BigDecimal(0) &&
                entry.metadata.get("batch_id").contains(batch.batchId.toString) &&
                entry.metadata.get("source").contains("scheduler")
            )
          }
        )
      }
    )
