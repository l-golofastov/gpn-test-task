package ru.gpn.scheduler

import ru.gpn.common.AppError
import zio.*

trait WasteRepository:
  def insertAll(wasteType: WasteType, entries: Chunk[WasteEntry]): IO[AppError, Int]
  def totalWeights: IO[AppError, Map[WasteType, BigDecimal]]
  def saveSnapshot(snapshot: WasteSnapshot): IO[AppError, Unit]
  def latestSnapshot: IO[AppError, Option[WasteSnapshot]]
  def listSnapshots(limit: Int): IO[AppError, List[WasteSnapshot]]
  def tableCounts: IO[AppError, Map[WasteType, Long]]
