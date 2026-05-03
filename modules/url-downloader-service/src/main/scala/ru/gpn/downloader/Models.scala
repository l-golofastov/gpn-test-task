package ru.gpn.downloader

import zio.json.*

final case class SavedPage(tag: String, page: Int, path: String) derives JsonCodec

final case class DownloadFailure(tag: String, page: Option[Int], message: String) derives JsonCodec

final case class DownloadResult(saved: List[SavedPage], failures: List[DownloadFailure]) derives JsonCodec:
  def ++(other: DownloadResult): DownloadResult =
    DownloadResult(saved ++ other.saved, failures ++ other.failures)

object DownloadResult:
  val empty: DownloadResult = DownloadResult(Nil, Nil)

final case class StartDownloadResponse(
    jobId: String,
    status: String,
    tags: List[String],
    createdAt: Long
) derives JsonCodec

final case class DownloadJobView(
    jobId: String,
    status: String,
    tags: List[String],
    createdAt: Long,
    finishedAt: Option[Long],
    saved: List[SavedPage],
    failures: List[DownloadFailure]
) derives JsonCodec

final case class HealthResponse(status: String) derives JsonCodec

object JobStatus:
  val Running = "running"
  val Succeeded = "succeeded"
  val Failed = "failed"
