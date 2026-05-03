package ru.gpn.downloader

import ru.gpn.common.http.SimpleHttpServer
import zio.*

object UrlDownloaderApp extends ZIOAppDefault:
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    for
      config <- DownloaderAppConfig.load
      client = JavaStackOverflowClient.live(config.downloader)
      storage = new FilePageStorage(config.downloader.outputPath)
      runner = new LiveDownloadRunner(config.downloader, client, storage)
      jobs <- InMemoryDownloadJobs.make(runner)
      _ <- SimpleHttpServer.serve(config.server, DownloadRoutes.router(jobs))
    yield ()
