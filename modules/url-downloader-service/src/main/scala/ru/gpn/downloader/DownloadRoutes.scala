package ru.gpn.downloader

import ru.gpn.common.AppError
import ru.gpn.common.http.{HttpResponse, Route, Router}
import zio.*

object DownloadRoutes:
  def router(jobs: DownloadJobs): Router =
    Router(
      List(
        Route("GET", "/health", _ => ZIO.succeed(HttpResponse.json(HealthResponse("ok")))),
        Route("GET", "/openapi.json", _ => ZIO.succeed(HttpResponse.text(DownloaderOpenApi.json, contentType = "application/json; charset=utf-8"))),
        Route("GET", "/swagger", _ => ZIO.succeed(HttpResponse.text(DownloaderOpenApi.swaggerHtml, contentType = "text/html; charset=utf-8"))),
        Route("GET", "/docs", _ => ZIO.succeed(HttpResponse.text(DownloaderOpenApi.swaggerHtml, contentType = "text/html; charset=utf-8"))),
        Route(
          "POST",
          "/downloads",
          request =>
            jobs
              .start(request.query.getOrElse("tagged", Nil))
              .map(response => HttpResponse.json(response, status = 202))
        ),
        Route(
          "GET",
          "/downloads/:jobId",
          request =>
            for
              jobId <- request.pathParam("jobId")
              response <- jobs.get(jobId)
            yield HttpResponse.json(response)
        )
      )
    )
