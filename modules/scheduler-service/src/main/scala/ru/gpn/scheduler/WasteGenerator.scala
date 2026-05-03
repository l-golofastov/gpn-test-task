package ru.gpn.scheduler

import zio.*

import java.util.UUID

object WasteGenerator:
  def generateAll(itemsPerType: Int): UIO[GeneratedWasteBatch] =
    for
      batchId <- ZIO.succeed(UUID.randomUUID())
      createdAt <- Clock.instant
      entries <- ZIO.foreach(WasteType.all) { wasteType =>
        ZIO.foreach(1 to itemsPerType)(index => generateOne(wasteType, batchId, createdAt, index))
          .map(items => wasteType -> Chunk.fromIterable(items))
      }
    yield GeneratedWasteBatch(batchId, createdAt, entries.toMap)

  private def generateOne(
      wasteType: WasteType,
      batchId: UUID,
      createdAt: java.time.Instant,
      index: Int
  ): UIO[WasteEntry] =
    for
      id <- ZIO.succeed(UUID.randomUUID())
      random <- Random.nextDouble
      weight = BigDecimal(wasteType.minWeightKg + random * (wasteType.maxWeightKg - wasteType.minWeightKg))
        .setScale(3, BigDecimal.RoundingMode.HALF_UP)
    yield WasteEntry(
      id = id,
      wasteType = wasteType,
      weightKg = weight,
      createdAt = createdAt,
      metadata = Map(
        "source" -> "scheduler",
        "batch_id" -> batchId.toString,
        "sequence" -> index.toString
      )
    )
