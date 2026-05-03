package ru.gpn.scheduler

import ru.gpn.common.AppError
import ru.gpn.common.http.{HttpRequest, HttpResponse, Route, Router}
import zio.*
import zio.json.*

enum TickMode(val id: String):
  case Generate extends TickMode("generate")
  case Snapshot extends TickMode("snapshot")
  case Both extends TickMode("both")

object TickMode:
  def parse(value: String): IO[AppError.Validation, TickMode] =
    value.trim.toLowerCase match
      case "generate" => ZIO.succeed(TickMode.Generate)
      case "snapshot" => ZIO.succeed(TickMode.Snapshot)
      case "both"     => ZIO.succeed(TickMode.Both)
      case other      => ZIO.fail(AppError.Validation(s"Unsupported tick mode: $other"))

import SchedulerJson.given

final case class HealthResponse(status: String) derives JsonCodec

final case class SchedulerConfigResponse(
    httpHost: String,
    httpPort: Int,
    generateIntervalMillis: Long,
    snapshotIntervalMillis: Long,
    itemsPerType: Int,
    statsLimit: Int
) derives JsonCodec

object SchedulerConfigResponse:
  def from(config: SchedulerConfig): SchedulerConfigResponse =
    SchedulerConfigResponse(
      httpHost = config.http.host,
      httpPort = config.http.port,
      generateIntervalMillis = config.generateIntervalMillis,
      snapshotIntervalMillis = config.snapshotIntervalMillis,
      itemsPerType = config.itemsPerType,
      statsLimit = config.statsLimit
    )

final case class StatusResponse(
    status: String,
    config: SchedulerConfigResponse,
    state: SchedulerState,
    rowCounts: Map[String, Long]
) derives JsonCodec

final case class StatsResponse(
    totals: WasteTotals,
    latestSnapshot: Option[WasteSnapshot],
    snapshots: List[WasteSnapshot]
) derives JsonCodec

final case class ManualTickResponse(
    mode: String,
    generated: Option[GenerateTickResult],
    snapshot: Option[WasteSnapshot]
) derives JsonCodec

object SchedulerRoutes:
  def router(scheduler: WasteScheduler, repository: WasteRepository, config: SchedulerConfig): Router =
    Router(
      List(
        Route("GET", "/health", _ => ZIO.succeed(HttpResponse.json(HealthResponse("ok")))),
        Route("GET", "/api/v1/health", _ => ZIO.succeed(HttpResponse.json(HealthResponse("ok")))),
        Route("GET", "/status", _ => status(scheduler, repository, config)),
        Route("GET", "/api/v1/status", _ => status(scheduler, repository, config)),
        Route("POST", "/tick", request => manualTick(request, scheduler)),
        Route("POST", "/api/v1/tick", request => manualTick(request, scheduler)),
        Route("GET", "/stats", request => stats(request, repository, config)),
        Route("GET", "/api/v1/stats", request => stats(request, repository, config)),
        Route(
          "GET",
          "/openapi.json",
          _ => ZIO.succeed(HttpResponse(200, OpenApi.json, "application/json; charset=utf-8"))
        ),
        Route(
          "GET",
          "/api/v1/openapi.json",
          _ => ZIO.succeed(HttpResponse(200, OpenApi.json, "application/json; charset=utf-8"))
        ),
        Route("GET", "/swagger", _ => ZIO.succeed(HttpResponse.text(OpenApi.swaggerHtml, 200, "text/html; charset=utf-8"))),
        Route("GET", "/docs", _ => ZIO.succeed(HttpResponse.text(OpenApi.swaggerHtml, 200, "text/html; charset=utf-8")))
      )
    )

  private def status(
      scheduler: WasteScheduler,
      repository: WasteRepository,
      config: SchedulerConfig
  ): IO[AppError, HttpResponse] =
    for
      state <- scheduler.state
      counts <- repository.tableCounts
    yield HttpResponse.json(
      StatusResponse(
        status = "running",
        config = SchedulerConfigResponse.from(config),
        state = state,
        rowCounts = counts.view.map { case (wasteType, count) => wasteType.id -> count }.toMap
      )
    )

  private def stats(request: HttpRequest, repository: WasteRepository, config: SchedulerConfig): IO[AppError, HttpResponse] =
    for
      limit <- positiveIntQuery(request, "limit", config.statsLimit)
      totals <- repository.totalWeights
      latest <- repository.latestSnapshot
      snapshots <- repository.listSnapshots(limit)
    yield HttpResponse.json(
      StatsResponse(
        totals = WasteTotals.fromMap(totals),
        latestSnapshot = latest,
        snapshots = snapshots
      )
    )

  private def manualTick(request: HttpRequest, scheduler: WasteScheduler): IO[AppError, HttpResponse] =
    for
      mode <- TickMode.parse(request.queryParam("mode").getOrElse(TickMode.Both.id))
      generated <- mode match
        case TickMode.Generate | TickMode.Both => scheduler.generateTick.map(Some(_))
        case TickMode.Snapshot                 => ZIO.succeed(None)
      snapshot <- mode match
        case TickMode.Snapshot | TickMode.Both => scheduler.snapshotTick.map(Some(_))
        case TickMode.Generate                 => ZIO.succeed(None)
    yield HttpResponse.json(
      ManualTickResponse(
        mode = mode.id,
        generated = generated,
        snapshot = snapshot
      )
    )

  private def positiveIntQuery(request: HttpRequest, name: String, default: Int): IO[AppError.Validation, Int] =
    request.queryParam(name) match
      case None => ZIO.succeed(default)
      case Some(value) =>
        ZIO
          .attempt(value.toInt)
          .mapError(_ => AppError.Validation(s"Query parameter $name must be an integer"))
          .flatMap { parsed =>
            ZIO.fail(AppError.Validation(s"Query parameter $name must be positive")).when(parsed <= 0).as(parsed)
          }
