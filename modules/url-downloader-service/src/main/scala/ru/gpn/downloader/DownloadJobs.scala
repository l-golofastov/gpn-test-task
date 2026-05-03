package ru.gpn.downloader

import ru.gpn.common.{AppError, Time}
import zio.*

trait DownloadJobs:
  def start(rawTags: List[String]): IO[AppError.Validation, StartDownloadResponse]
  def get(jobId: String): IO[AppError.NotFound, DownloadJobView]

final class InMemoryDownloadJobs private (runner: DownloadRunner, jobs: Ref[Map[String, DownloadJobView]]) extends DownloadJobs:
  override def start(rawTags: List[String]): IO[AppError.Validation, StartDownloadResponse] =
    for
      tags <- TagParser.parse(rawTags)
      jobId <- Random.nextUUID.map(_.toString)
      createdAt <- Time.epochSeconds
      view = DownloadJobView(jobId, JobStatus.Running, tags, createdAt, None, Nil, Nil)
      _ <- jobs.update(_.updated(jobId, view))
      _ <- completeInBackground(jobId, tags).forkDaemon
    yield StartDownloadResponse(jobId, JobStatus.Running, tags, createdAt)

  override def get(jobId: String): IO[AppError.NotFound, DownloadJobView] =
    jobs.get.flatMap { state =>
      ZIO.fromOption(state.get(jobId)).orElseFail(AppError.NotFound(s"Download job $jobId was not found"))
    }

  private def completeInBackground(jobId: String, tags: List[String]): UIO[Unit] =
    runner
      .download(tags)
      .catchAllCause { cause =>
        ZIO.succeed(DownloadResult(Nil, List(DownloadFailure("*", None, cause.prettyPrint))))
      }
      .flatMap(finish(jobId, _))

  private def finish(jobId: String, result: DownloadResult): UIO[Unit] =
    Time.epochSeconds.flatMap { finishedAt =>
      jobs.update { state =>
        state.updatedWith(jobId) {
          case Some(current) =>
            Some(
              current.copy(
                status = if result.failures.isEmpty then JobStatus.Succeeded else JobStatus.Failed,
                finishedAt = Some(finishedAt),
                saved = result.saved,
                failures = result.failures
              )
            )
          case None => None
        }
      }
    }

object InMemoryDownloadJobs:
  def make(runner: DownloadRunner): UIO[InMemoryDownloadJobs] =
    Ref.make(Map.empty[String, DownloadJobView]).map(new InMemoryDownloadJobs(runner, _))

object TagParser:
  def parse(rawTags: List[String]): IO[AppError.Validation, List[String]] =
    val tags = rawTags
      .flatMap(_.split("[,;]").toList)
      .map(_.trim)
      .filter(_.nonEmpty)
      .distinct

    ZIO.fail(AppError.Validation("Missing query parameter: tagged")).when(tags.isEmpty).as(tags)
