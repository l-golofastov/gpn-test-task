package ru.gpn.scheduler

import ru.gpn.common.AppError
import zio.*

import java.util.UUID

final class WasteScheduler private (
    repository: WasteRepository,
    config: SchedulerConfig,
    stateRef: Ref[SchedulerState]
):
  def state: UIO[SchedulerState] =
    stateRef.get

  def generateTick: IO[AppError, GenerateTickResult] =
    val effect: IO[AppError, GenerateTickResult] = for
      batch <- WasteGenerator.generateAll(config.itemsPerType)
      inserted <- ZIO.foreachPar(batch.entries.toList) { case (wasteType, entries) =>
        repository.insertAll(wasteType, entries).map(wasteType.id -> _)
      }
      result = GenerateTickResult(
        batchId = batch.batchId,
        generatedAt = batch.createdAt,
        insertedByType = inserted.toMap,
        totalInserted = inserted.map(_._2).sum
      )
      _ <- stateRef.update(_.recordGenerated(batch.createdAt))
    yield result

    effect.tapError(error => stateRef.update(_.recordError(error.detail)))

  def snapshotTick: IO[AppError, WasteSnapshot] =
    val effect: IO[AppError, WasteSnapshot] = for
      totals <- repository.totalWeights
      capturedAt <- Clock.instant
      snapshot = WasteSnapshot(
        id = UUID.randomUUID(),
        capturedAt = capturedAt,
        totals = WasteTotals.fromMap(totals)
      )
      _ <- repository.saveSnapshot(snapshot)
      _ <- stateRef.update(_.recordSnapshot(capturedAt))
    yield snapshot

    effect.tapError(error => stateRef.update(_.recordError(error.detail)))

  def start: URIO[Scope, Unit] =
    generateLoop.forkScoped.unit *> snapshotLoop.forkScoped.unit

  private def generateLoop: UIO[Unit] =
    (ZIO.sleep(config.generateInterval) *> generateTick.catchAll(recordError).unit).forever

  private def snapshotLoop: UIO[Unit] =
    (ZIO.sleep(config.snapshotInterval) *> snapshotTick.catchAll(recordError).unit).forever

  private def recordError(error: AppError): UIO[Unit] =
    stateRef.update(_.recordError(error.detail))

object WasteScheduler:
  def make(repository: WasteRepository, config: SchedulerConfig): UIO[WasteScheduler] =
    for
      startedAt <- Clock.instant
      stateRef <- Ref.make(SchedulerState.initial(startedAt))
    yield WasteScheduler(repository, config, stateRef)
