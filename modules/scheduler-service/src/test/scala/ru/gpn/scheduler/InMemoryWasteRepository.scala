package ru.gpn.scheduler

import ru.gpn.common.AppError
import zio.*

final class InMemoryWasteRepository private (
    recordsRef: Ref[Map[WasteType, Vector[WasteEntry]]],
    snapshotsRef: Ref[Vector[WasteSnapshot]]
) extends WasteRepository:
  override def insertAll(wasteType: WasteType, entries: Chunk[WasteEntry]): IO[AppError, Int] =
    if entries.exists(_.wasteType != wasteType) then
      ZIO.fail(AppError.Validation(s"Batch contains records outside ${wasteType.id}"))
    else
      recordsRef
        .update { records =>
          records.updated(wasteType, records.getOrElse(wasteType, Vector.empty) ++ entries.toList.toVector)
        }
        .as(entries.size)

  override def totalWeights: IO[AppError, Map[WasteType, BigDecimal]] =
    recordsRef.get.map { records =>
      WasteType.all.map { wasteType =>
        wasteType -> records.getOrElse(wasteType, Vector.empty).foldLeft(BigDecimal(0))(_ + _.weightKg)
      }.toMap
    }

  override def saveSnapshot(snapshot: WasteSnapshot): IO[AppError, Unit] =
    snapshotsRef.update(_ :+ snapshot)

  override def latestSnapshot: IO[AppError, Option[WasteSnapshot]] =
    snapshotsRef.get.map(_.lastOption)

  override def listSnapshots(limit: Int): IO[AppError, List[WasteSnapshot]] =
    snapshotsRef.get.map(_.reverse.take(limit).toList)

  override def tableCounts: IO[AppError, Map[WasteType, Long]] =
    recordsRef.get.map { records =>
      WasteType.all.map(wasteType => wasteType -> records.getOrElse(wasteType, Vector.empty).size.toLong).toMap
    }

object InMemoryWasteRepository:
  def make: UIO[InMemoryWasteRepository] =
    for
      records <- Ref.make(WasteType.all.map(_ -> Vector.empty[WasteEntry]).toMap)
      snapshots <- Ref.make(Vector.empty[WasteSnapshot])
    yield InMemoryWasteRepository(records, snapshots)
