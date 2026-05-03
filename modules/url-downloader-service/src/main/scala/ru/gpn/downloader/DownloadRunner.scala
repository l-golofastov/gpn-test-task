package ru.gpn.downloader

import zio.*

trait DownloadRunner:
  def download(tags: List[String]): UIO[DownloadResult]

final class LiveDownloadRunner(config: DownloaderConfig, client: StackOverflowClient, storage: PageStorage) extends DownloadRunner:
  override def download(tags: List[String]): UIO[DownloadResult] =
    ZIO
      .foreachPar(tags)(downloadTag)
      .withParallelism(config.maxParallelTags)
      .map(results => normalize(results.foldLeft(DownloadResult.empty)(_ ++ _)))

  private def downloadTag(tag: String): UIO[DownloadResult] =
    def loop(page: Int, saved: List[SavedPage]): UIO[DownloadResult] =
      if page > config.pages then ZIO.succeed(DownloadResult(saved.reverse, Nil))
      else
        downloadPage(tag, page).foldZIO(
          failure => ZIO.succeed(DownloadResult(saved.reverse, List(failure))),
          savedPage => loop(page + 1, savedPage :: saved)
        )

    loop(page = 1, saved = Nil)

  private def downloadPage(tag: String, page: Int): IO[DownloadFailure, SavedPage] =
    for
      json <- client.search(tag, page).mapError(error => DownloadFailure(tag, Some(page), error.detail))
      path <- storage.save(tag, page, json).mapError(error => DownloadFailure(tag, Some(page), error.detail))
    yield SavedPage(tag, page, path.toAbsolutePath.normalize.toString)

  private def normalize(result: DownloadResult): DownloadResult =
    result.copy(
      saved = result.saved.sortBy(page => (page.tag, page.page)),
      failures = result.failures.sortBy(failure => (failure.tag, failure.page.getOrElse(Int.MaxValue), failure.message))
    )
