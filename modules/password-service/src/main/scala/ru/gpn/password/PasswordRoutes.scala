package ru.gpn.password

import ru.gpn.common.{AppError, Time}
import ru.gpn.common.http.{HttpRequest, HttpResponse, Route, Router}
import zio.*
import zio.json.*

import java.util.UUID
import scala.util.Try

object PasswordRoutes:
  private val DefaultLimit = 100
  private val MaxLimit = 1000
  private val ExportLimit = 100000
  private val DefaultOffset = 0
  private val DefaultOldDays = 365
  private val DefaultOldestLimit = 20

  def router(repository: PasswordRepository): Router =
    Router(
      List(
        Route("GET", "/health", _ => ZIO.succeed(HttpResponse.json(HealthResponse("ok")))),
        Route("GET", "/openapi.json", _ => ZIO.succeed(HttpResponse.text(PasswordOpenApi.json, contentType = "application/json; charset=utf-8"))),
        Route("GET", "/swagger", _ => ZIO.succeed(HttpResponse.text(PasswordOpenApi.swaggerHtml, contentType = "text/html; charset=utf-8"))),
        Route("GET", "/swagger-ui", _ => ZIO.succeed(HttpResponse.text(PasswordOpenApi.swaggerHtml, contentType = "text/html; charset=utf-8"))),
        Route("GET", "/api/passwords/search", request => search(repository, request)),
        Route("GET", "/api/passwords/export", request => exportCsv(repository, request)),
        Route("POST", "/api/passwords/import", request => importCsv(repository, request)),
        Route("GET", "/api/passwords/stats", request => stats(repository, request)),
        Route("GET", "/api/passwords/:id/history", request => history(repository, request)),
        Route("GET", "/api/passwords/:id", request => get(repository, request)),
        Route("PUT", "/api/passwords/:id", request => update(repository, request)),
        Route("DELETE", "/api/passwords/:id", request => delete(repository, request)),
        Route("GET", "/api/passwords", request => list(repository, request)),
        Route("POST", "/api/passwords", request => create(repository, request))
      )
    )

  private def create(repository: PasswordRepository, request: HttpRequest): IO[AppError, HttpResponse] =
    for
      body <- request.jsonBody[CreatePasswordRequest]
      input <- PasswordValidation.create(body)
      now <- Time.epochSeconds
      record <- repository.create(input, now)
    yield HttpResponse.json(record, status = 201)

  private def get(repository: PasswordRepository, request: HttpRequest): IO[AppError, HttpResponse] =
    for
      id <- uuidPath(request, "id")
      includeDeleted <- booleanQuery(request, "includeDeleted", default = false)
      record <- repository.get(id, includeDeleted)
    yield HttpResponse.json(record)

  private def list(repository: PasswordRepository, request: HttpRequest): IO[AppError, HttpResponse] =
    for
      includeDeleted <- booleanQuery(request, "includeDeleted", default = false)
      limit <- intQuery(request, "limit", DefaultLimit, min = 1, max = MaxLimit)
      offset <- intQuery(request, "offset", DefaultOffset, min = 0, max = Int.MaxValue)
      records <- repository.list(includeDeleted, limit, offset)
    yield HttpResponse.json(records)

  private def search(repository: PasswordRepository, request: HttpRequest): IO[AppError, HttpResponse] =
    for
      rawQuery <- request.requiredQueryParam("q")
      query <- nonEmptyQuery(rawQuery)
      field <- ZIO.fromEither(PasswordSearchField.parse(request.queryParam("field"))).mapError(AppError.Validation.apply)
      mode <- ZIO.fromEither(PasswordSearchMode.parse(request.queryParam("mode"))).mapError(AppError.Validation.apply)
      includeDeleted <- booleanQuery(request, "includeDeleted", default = false)
      limit <- intQuery(request, "limit", DefaultLimit, min = 1, max = MaxLimit)
      offset <- intQuery(request, "offset", DefaultOffset, min = 0, max = Int.MaxValue)
      records <- repository.search(query, field, mode, includeDeleted, limit, offset)
    yield HttpResponse.json(records)

  private def update(repository: PasswordRepository, request: HttpRequest): IO[AppError, HttpResponse] =
    for
      id <- uuidPath(request, "id")
      body <- request.jsonBody[UpdatePasswordRequest]
      patch <- PasswordValidation.patch(body)
      now <- Time.epochSeconds
      record <- repository.update(id, patch, now)
    yield HttpResponse.json(record)

  private def delete(repository: PasswordRepository, request: HttpRequest): IO[AppError, HttpResponse] =
    for
      id <- uuidPath(request, "id")
      now <- Time.epochSeconds
      _ <- repository.delete(id, now)
    yield HttpResponse.empty(204)

  private def history(repository: PasswordRepository, request: HttpRequest): IO[AppError, HttpResponse] =
    for
      id <- uuidPath(request, "id")
      entries <- repository.history(id)
    yield HttpResponse.json(entries)

  private def stats(repository: PasswordRepository, request: HttpRequest): IO[AppError, HttpResponse] =
    for
      oldDays <- intQuery(request, "oldDays", DefaultOldDays, min = 0, max = 36500)
      oldestLimit <- intQuery(request, "oldestLimit", DefaultOldestLimit, min = 1, max = MaxLimit)
      now <- Time.epochSeconds
      response <- repository.stats(now, oldDays, oldestLimit)
    yield HttpResponse.json(response)

  private def exportCsv(repository: PasswordRepository, request: HttpRequest): IO[AppError, HttpResponse] =
    for
      includeDeleted <- booleanQuery(request, "includeDeleted", default = false)
      limit <- intQuery(request, "limit", ExportLimit, min = 1, max = ExportLimit)
      records <- repository.list(includeDeleted, limit, 0)
    yield HttpResponse.text(
      PasswordCsv.render(records),
      contentType = "text/csv; charset=utf-8"
    ).copy(headers = Map("Content-Disposition" -> """attachment; filename="passwords.csv""""))

  private def importCsv(repository: PasswordRepository, request: HttpRequest): IO[AppError, HttpResponse] =
    for
      parsed <- ZIO.fromEither(PasswordCsv.parseImport(request.body)).mapError(AppError.Validation.apply)
      now <- Time.epochSeconds
      imported <- ZIO.foreach(parsed.records)(record => repository.create(record, now))
    yield HttpResponse.json(
      CsvImportResult(
        imported = imported.size,
        failed = parsed.errors.size,
        errors = parsed.errors
      )
    )

  private def uuidPath(request: HttpRequest, name: String): IO[AppError.Validation, UUID] =
    request.pathParam(name).flatMap { raw =>
      ZIO
        .fromTry(Try(UUID.fromString(raw)))
        .mapError(_ => AppError.Validation(s"Invalid UUID path parameter: $name"))
    }

  private def nonEmptyQuery(value: String): IO[AppError.Validation, String] =
    val trimmed = value.trim
    if trimmed.isEmpty then ZIO.fail(AppError.Validation("Query parameter q must not be empty"))
    else ZIO.succeed(trimmed)

  private def booleanQuery(request: HttpRequest, name: String, default: Boolean): IO[AppError.Validation, Boolean] =
    request.queryParam(name) match
      case None => ZIO.succeed(default)
      case Some(value) =>
        value.trim.toLowerCase match
          case "true" | "1" | "yes" => ZIO.succeed(true)
          case "false" | "0" | "no" => ZIO.succeed(false)
          case _ => ZIO.fail(AppError.Validation(s"Query parameter $name must be a boolean"))

  private def intQuery(
      request: HttpRequest,
      name: String,
      default: Int,
      min: Int,
      max: Int
  ): IO[AppError.Validation, Int] =
    request.queryParam(name) match
      case None => ZIO.succeed(default)
      case Some(value) =>
        ZIO
          .fromTry(Try(value.toInt))
          .mapError(_ => AppError.Validation(s"Query parameter $name must be an integer"))
          .flatMap { number =>
            if number < min || number > max then
              ZIO.fail(AppError.Validation(s"Query parameter $name must be between $min and $max"))
            else ZIO.succeed(number)
          }
