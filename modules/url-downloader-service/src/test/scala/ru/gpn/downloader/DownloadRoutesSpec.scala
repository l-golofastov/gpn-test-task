package ru.gpn.downloader

import ru.gpn.common.http.HttpRequest
import zio.*
import zio.json.*
import zio.test.*

object DownloadRoutesSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("DownloadRoutes")(
      test("starts a download job from repeated and comma-separated tagged query params") {
        for
          jobs <- InMemoryDownloadJobs.make(new DownloadRunner:
            override def download(tags: List[String]): UIO[DownloadResult] =
              ZIO.succeed(DownloadResult.empty)
          )
          response <- DownloadRoutes.router(jobs).handle(
            HttpRequest(
              method = "POST",
              path = List("downloads"),
              query = Map("tagged" -> List("scala,zio", "cats")),
              headers = Map.empty,
              body = ""
            )
          )
          body <- parseJson[StartDownloadResponse](response.body)
        yield assertTrue(
          response.status == 202,
          body.status == JobStatus.Running,
          body.tags == List("scala", "zio", "cats"),
          body.jobId.nonEmpty
        )
      },
      test("returns validation error when tagged is missing") {
        for
          jobs <- InMemoryDownloadJobs.make(new DownloadRunner:
            override def download(tags: List[String]): UIO[DownloadResult] =
              ZIO.succeed(DownloadResult.empty)
          )
          response <- DownloadRoutes.router(jobs).handle(
            HttpRequest(
              method = "POST",
              path = List("downloads"),
              query = Map.empty,
              headers = Map.empty,
              body = ""
            )
          )
        yield assertTrue(response.status == 400)
      },
      test("returns stored job by id") {
        for
          jobs <- InMemoryDownloadJobs.make(new DownloadRunner:
            override def download(tags: List[String]): UIO[DownloadResult] =
              ZIO.succeed(DownloadResult(List(SavedPage("scala", 1, "/tmp/page-1.json")), Nil))
          )
          router = DownloadRoutes.router(jobs)
          startResponse <- router.handle(
            HttpRequest(
              method = "POST",
              path = List("downloads"),
              query = Map("tagged" -> List("scala")),
              headers = Map.empty,
              body = ""
            )
          )
          startBody <- parseJson[StartDownloadResponse](startResponse.body)
          getResponse <- router.handle(
            HttpRequest(
              method = "GET",
              path = List("downloads", startBody.jobId),
              query = Map.empty,
              headers = Map.empty,
              body = ""
            )
          )
          getBody <- parseJson[DownloadJobView](getResponse.body)
        yield assertTrue(
          getResponse.status == 200,
          getBody.jobId == startBody.jobId,
          getBody.tags == List("scala")
        )
      }
    )

  private def parseJson[A: JsonDecoder](body: String): UIO[A] =
    ZIO.fromEither(body.fromJson[A]).mapError(error => RuntimeException(error)).orDie
