package ru.gpn.downloader

import ru.gpn.common.{AppError, Env}
import ru.gpn.common.http.ServerConfig
import zio.*

import java.nio.file.{Path, Paths}
import java.time.Duration as JDuration
import java.util.Properties

final case class DownloaderConfig(
    pages: Int,
    pageSize: Int,
    sort: String,
    order: String,
    outputPath: Path,
    maxParallelTags: Int,
    stackExchangeBaseUrl: String,
    requestTimeout: JDuration
)

final case class DownloaderAppConfig(server: ServerConfig, downloader: DownloaderConfig)

final case class DownloaderDefaults(
    pages: Int,
    pageSize: Int,
    sort: String,
    order: String,
    outputPath: String,
    maxParallelTags: Int,
    stackExchangeBaseUrl: String,
    requestTimeoutSeconds: Long,
    serverHost: String,
    serverPort: Int
)

object DownloaderDefaults:
  val builtIn: DownloaderDefaults =
    DownloaderDefaults(
      pages = 1,
      pageSize = 10,
      sort = "activity",
      order = "desc",
      outputPath = "./data/url-downloads",
      maxParallelTags = 4,
      stackExchangeBaseUrl = "https://api.stackexchange.com/2.3",
      requestTimeoutSeconds = 20,
      serverHost = "0.0.0.0",
      serverPort = 8082
    )

object DownloaderAppConfig:
  private val ResourceName = "url-downloader.properties"

  def load: IO[AppError.Config, DownloaderAppConfig] =
    for
      env <- Env.live
      defaults <- loadDefaults
      config <- from(env, defaults)
    yield config

  def from(env: Env, defaults: DownloaderDefaults = DownloaderDefaults.builtIn): IO[AppError.Config, DownloaderAppConfig] =
    for
      pages <- env.positiveInt("URL_DOWNLOADER_PAGES", defaults.pages)
      pageSize <- env.positiveInt("URL_DOWNLOADER_PAGE_SIZE", defaults.pageSize)
      _ <- ZIO
        .fail(AppError.Config("Environment variable URL_DOWNLOADER_PAGE_SIZE must be between 1 and 100"))
        .when(pageSize > 100)
      sort <- nonEmpty("URL_DOWNLOADER_SORT", env.string("URL_DOWNLOADER_SORT", defaults.sort))
      order <- nonEmpty("URL_DOWNLOADER_ORDER", env.string("URL_DOWNLOADER_ORDER", defaults.order).toLowerCase)
      _ <- ZIO
        .fail(AppError.Config("Environment variable URL_DOWNLOADER_ORDER must be asc or desc"))
        .unless(Set("asc", "desc").contains(order))
      outputPath <- ZIO.succeed(Paths.get(env.string("URL_DOWNLOADER_PATH", defaults.outputPath)).toAbsolutePath.normalize)
      maxParallelTags <- env.positiveInt("URL_DOWNLOADER_MAX_PARALLEL", defaults.maxParallelTags)
      baseUrl <- nonEmpty(
        "URL_DOWNLOADER_STACKEXCHANGE_BASE_URL",
        env.string("URL_DOWNLOADER_STACKEXCHANGE_BASE_URL", defaults.stackExchangeBaseUrl)
      )
      timeoutSeconds <- env.positiveLong("URL_DOWNLOADER_REQUEST_TIMEOUT_SECONDS", defaults.requestTimeoutSeconds)
      host <- nonEmpty("URL_DOWNLOADER_SERVER_HOST", env.string("URL_DOWNLOADER_SERVER_HOST", defaults.serverHost))
      port <- env.positiveInt("URL_DOWNLOADER_SERVER_PORT", defaults.serverPort)
      _ <- ZIO.fail(AppError.Config("Environment variable URL_DOWNLOADER_SERVER_PORT must be <= 65535")).when(port > 65535)
    yield DownloaderAppConfig(
      server = ServerConfig(host, port),
      downloader = DownloaderConfig(
        pages = pages,
        pageSize = pageSize,
        sort = sort,
        order = order,
        outputPath = outputPath,
        maxParallelTags = maxParallelTags,
        stackExchangeBaseUrl = baseUrl,
        requestTimeout = JDuration.ofSeconds(timeoutSeconds)
      )
    )

  private def nonEmpty(name: String, value: String): IO[AppError.Config, String] =
    val trimmed = value.trim
    ZIO.fail(AppError.Config(s"Environment variable $name must not be empty")).when(trimmed.isEmpty).as(trimmed)

  private def loadDefaults: IO[AppError.Config, DownloaderDefaults] =
    ZIO
      .attemptBlocking {
        val properties = Properties()
        val loader = Option(Thread.currentThread().getContextClassLoader).getOrElse(getClass.getClassLoader)
        Option(loader.getResourceAsStream(ResourceName)).foreach { stream =>
          try properties.load(stream)
          finally stream.close()
        }

        def string(name: String, default: String): String =
          Option(properties.getProperty(name)).filter(_.trim.nonEmpty).map(_.trim).getOrElse(default)

        def int(name: String, default: Int): Int =
          string(name, default.toString).toInt

        def long(name: String, default: Long): Long =
          string(name, default.toString).toLong

        DownloaderDefaults(
          pages = int("urlDownloader.pages", DownloaderDefaults.builtIn.pages),
          pageSize = int("urlDownloader.pageSize", DownloaderDefaults.builtIn.pageSize),
          sort = string("urlDownloader.sort", DownloaderDefaults.builtIn.sort),
          order = string("urlDownloader.order", DownloaderDefaults.builtIn.order),
          outputPath = string("urlDownloader.path", DownloaderDefaults.builtIn.outputPath),
          maxParallelTags = int("urlDownloader.maxParallel", DownloaderDefaults.builtIn.maxParallelTags),
          stackExchangeBaseUrl =
            string("urlDownloader.stackExchangeBaseUrl", DownloaderDefaults.builtIn.stackExchangeBaseUrl),
          requestTimeoutSeconds = long(
            "urlDownloader.requestTimeoutSeconds",
            DownloaderDefaults.builtIn.requestTimeoutSeconds
          ),
          serverHost = string("urlDownloader.serverHost", DownloaderDefaults.builtIn.serverHost),
          serverPort = int("urlDownloader.serverPort", DownloaderDefaults.builtIn.serverPort)
        )
      }
      .mapError(error => AppError.Config(s"Cannot load $ResourceName: ${Option(error.getMessage).getOrElse(error.toString)}"))
