package ru.gpn.scheduler

import ru.gpn.common.JsonSupport.given
import zio.json.*

import java.time.Instant
import java.util.UUID
import scala.util.Try

enum WasteType(
    val id: String,
    val tableName: String,
    val snapshotColumn: String,
    val minWeightKg: Double,
    val maxWeightKg: Double
):
  case Plastic extends WasteType("plastic", "waste_plastic", "plastic_weight", 0.050, 2.500)
  case Glass extends WasteType("glass", "waste_glass", "glass_weight", 0.100, 4.000)
  case Paper extends WasteType("paper", "waste_paper", "paper_weight", 0.020, 1.500)
  case Other extends WasteType("other", "waste_other", "other_weight", 0.100, 5.000)

object WasteType:
  val all: List[WasteType] = values.toList

  def fromId(value: String): Option[WasteType] =
    all.find(_.id == value.trim.toLowerCase)

  private val encoder: JsonEncoder[WasteType] =
    JsonEncoder[String].contramap(_.id)

  private val decoder: JsonDecoder[WasteType] =
    JsonDecoder[String].mapOrFail(value => fromId(value).toRight(s"Unknown waste type: $value"))

  given JsonEncoder[WasteType] = encoder
  given JsonDecoder[WasteType] = decoder
  given JsonCodec[WasteType] = JsonCodec(encoder, decoder)

object SchedulerJson:
  private val instantEncoder: JsonEncoder[Instant] =
    JsonEncoder[String].contramap(_.toString)

  private val instantDecoder: JsonDecoder[Instant] =
    JsonDecoder[String].mapOrFail(value => Try(Instant.parse(value)).toEither.left.map(_ => s"Invalid instant: $value"))

  given JsonEncoder[Instant] = instantEncoder
  given JsonDecoder[Instant] = instantDecoder
  given JsonCodec[Instant] = JsonCodec(instantEncoder, instantDecoder)

import SchedulerJson.given
import WasteType.given

final case class WasteTotals(
    plastic: BigDecimal,
    glass: BigDecimal,
    paper: BigDecimal,
    other: BigDecimal,
    total: BigDecimal
) derives JsonCodec

object WasteTotals:
  val zero: WasteTotals =
    WasteTotals(BigDecimal(0), BigDecimal(0), BigDecimal(0), BigDecimal(0), BigDecimal(0))

  def fromMap(values: Map[WasteType, BigDecimal]): WasteTotals =
    fromValues(
      plastic = values.getOrElse(WasteType.Plastic, BigDecimal(0)),
      glass = values.getOrElse(WasteType.Glass, BigDecimal(0)),
      paper = values.getOrElse(WasteType.Paper, BigDecimal(0)),
      other = values.getOrElse(WasteType.Other, BigDecimal(0))
    )

  def fromValues(plastic: BigDecimal, glass: BigDecimal, paper: BigDecimal, other: BigDecimal): WasteTotals =
    WasteTotals(
      plastic = plastic,
      glass = glass,
      paper = paper,
      other = other,
      total = plastic + glass + paper + other
    )

  def toMap(totals: WasteTotals): Map[WasteType, BigDecimal] =
    Map(
      WasteType.Plastic -> totals.plastic,
      WasteType.Glass -> totals.glass,
      WasteType.Paper -> totals.paper,
      WasteType.Other -> totals.other
    )

final case class WasteEntry(
    id: UUID,
    wasteType: WasteType,
    weightKg: BigDecimal,
    createdAt: Instant,
    metadata: Map[String, String]
) derives JsonCodec

final case class GeneratedWasteBatch(
    batchId: UUID,
    createdAt: Instant,
    entries: Map[WasteType, zio.Chunk[WasteEntry]]
)

final case class GenerateTickResult(
    batchId: UUID,
    generatedAt: Instant,
    insertedByType: Map[String, Int],
    totalInserted: Int
) derives JsonCodec

final case class WasteSnapshot(
    id: UUID,
    capturedAt: Instant,
    totals: WasteTotals
) derives JsonCodec

final case class SchedulerState(
    startedAt: Instant,
    generatedTicks: Long,
    snapshotTicks: Long,
    lastGeneratedAt: Option[Instant],
    lastSnapshotAt: Option[Instant],
    lastError: Option[String]
) derives JsonCodec:
  def recordGenerated(at: Instant): SchedulerState =
    copy(
      generatedTicks = generatedTicks + 1,
      lastGeneratedAt = Some(at),
      lastError = None
    )

  def recordSnapshot(at: Instant): SchedulerState =
    copy(
      snapshotTicks = snapshotTicks + 1,
      lastSnapshotAt = Some(at),
      lastError = None
    )

  def recordError(message: String): SchedulerState =
    copy(lastError = Some(message))

object SchedulerState:
  def initial(startedAt: Instant): SchedulerState =
    SchedulerState(
      startedAt = startedAt,
      generatedTicks = 0L,
      snapshotTicks = 0L,
      lastGeneratedAt = None,
      lastSnapshotAt = None,
      lastError = None
    )
