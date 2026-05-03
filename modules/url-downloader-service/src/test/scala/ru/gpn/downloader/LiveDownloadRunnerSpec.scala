package ru.gpn.downloader

import ru.gpn.common.AppError
import zio.*
import zio.test.*

import java.nio.file.Path
import java.time.Duration as JDuration

object LiveDownloadRunnerSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("LiveDownloadRunner")(
      test("downloads all configured pages and caps parallel tag requests") {
        for
          temp <- tempDir
          current <- Ref.make(0)
          maxSeen <- Ref.make(0)
          calls <- Ref.make(List.empty[(String, Int)])
          client = new StackOverflowClient:
            override def search(tag: String, page: Int): IO[AppError.External, String] =
              (for
                active <- current.updateAndGet(_ + 1)
                _ <- maxSeen.update(previous => previous.max(active))
                _ <- calls.update((tag, page) :: _)
                _ <- ZIO.attemptBlocking(Thread.sleep(25)).orDie
              yield s"""{"items":[],"tag":"$tag","page":$page}""")
                .ensuring(current.update(_ - 1))
          runner = new LiveDownloadRunner(config(temp, pages = 2, maxParallel = 2), client, new FilePageStorage(temp))
          result <- runner.download(List("scala", "zio", "cats"))
          max <- maxSeen.get
          seenCalls <- calls.get
          scalaPage <- ZIO.attempt(_root_.java.nio.file.Files.readString(temp.resolve("scala").resolve("page-1.json")))
        yield assertTrue(
          result.failures.isEmpty,
          result.saved.size == 6,
          max == 2,
          seenCalls.toSet == Set(
            "scala" -> 1,
            "scala" -> 2,
            "zio" -> 1,
            "zio" -> 2,
            "cats" -> 1,
            "cats" -> 2
          ),
          scalaPage.contains(""""tag":"scala"""")
        )
      },
      test("keeps already saved pages and stops a tag on first failed page") {
        for
          temp <- tempDir
          client = new StackOverflowClient:
            override def search(tag: String, page: Int): IO[AppError.External, String] =
              if page == 2 then ZIO.fail(AppError.External("test failure"))
              else ZIO.succeed(s"""{"items":[],"tag":"$tag","page":$page}""")
          runner = new LiveDownloadRunner(config(temp, pages = 3, maxParallel = 1), client, new FilePageStorage(temp))
          result <- runner.download(List("scala"))
          page1Exists <- ZIO.attempt(_root_.java.nio.file.Files.exists(temp.resolve("scala").resolve("page-1.json")))
          page2Exists <- ZIO.attempt(_root_.java.nio.file.Files.exists(temp.resolve("scala").resolve("page-2.json")))
        yield assertTrue(
          result.saved.map(_.page) == List(1),
          result.failures.map(_.page) == List(Some(2)),
          result.failures.head.message == "test failure",
          page1Exists,
          !page2Exists
        )
      },
      test("uses filesystem-safe tag directory names") {
        for
          temp <- tempDir
          storage = new FilePageStorage(temp)
          path <- storage.save("c#/.net", 1, """{"items":[]}""")
          exists <- ZIO.attempt(_root_.java.nio.file.Files.exists(path))
        yield assertTrue(
          path.getParent.getFileName.toString == "c_0023_002F.net",
          exists
        )
      }
    )

  private def tempDir: Task[Path] =
    ZIO.attempt(_root_.java.nio.file.Files.createTempDirectory("url-downloader-runner-"))

  private def config(path: Path, pages: Int, maxParallel: Int): DownloaderConfig =
    DownloaderConfig(
      pages = pages,
      pageSize = 10,
      sort = "activity",
      order = "desc",
      outputPath = path,
      maxParallelTags = maxParallel,
      stackExchangeBaseUrl = "http://localhost",
      requestTimeout = JDuration.ofSeconds(1)
    )
