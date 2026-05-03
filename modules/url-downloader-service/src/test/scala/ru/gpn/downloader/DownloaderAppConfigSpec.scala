package ru.gpn.downloader

import ru.gpn.common.{AppError, Env}
import zio.*
import zio.test.*

object DownloaderAppConfigSpec extends ZIOSpecDefault:
  private val defaults = DownloaderDefaults.builtIn.copy(outputPath = "/tmp/url-downloader-default")

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("DownloaderAppConfig")(
      test("loads downloader and server values from environment") {
        val env = Env(
          Map(
            "URL_DOWNLOADER_PAGES" -> "3",
            "URL_DOWNLOADER_PAGE_SIZE" -> "25",
            "URL_DOWNLOADER_SORT" -> "votes",
            "URL_DOWNLOADER_ORDER" -> "ASC",
            "URL_DOWNLOADER_PATH" -> "/tmp/url-downloader-test",
            "URL_DOWNLOADER_MAX_PARALLEL" -> "2",
            "URL_DOWNLOADER_STACKEXCHANGE_BASE_URL" -> "http://localhost:18080/api",
            "URL_DOWNLOADER_REQUEST_TIMEOUT_SECONDS" -> "7",
            "URL_DOWNLOADER_SERVER_HOST" -> "127.0.0.1",
            "URL_DOWNLOADER_SERVER_PORT" -> "18081"
          )
        )

        DownloaderAppConfig.from(env, defaults).map { config =>
          assertTrue(
            config.downloader.pages == 3,
            config.downloader.pageSize == 25,
            config.downloader.sort == "votes",
            config.downloader.order == "asc",
            config.downloader.outputPath.toString == "/tmp/url-downloader-test",
            config.downloader.maxParallelTags == 2,
            config.downloader.stackExchangeBaseUrl == "http://localhost:18080/api",
            config.downloader.requestTimeout.getSeconds == 7L,
            config.server.host == "127.0.0.1",
            config.server.port == 18081
          )
        }
      },
      test("rejects StackExchange page sizes over 100") {
        DownloaderAppConfig
          .from(Env(Map("URL_DOWNLOADER_PAGE_SIZE" -> "101")), defaults)
          .exit
          .map { exit =>
            assertTrue(exit match
              case Exit.Failure(cause) =>
                cause.failureOption.exists {
                  case error: AppError.Config => error.detail.contains("between 1 and 100")
                  case _                      => false
                }
              case _ => false
            )
          }
      },
      test("rejects unsupported order values") {
        DownloaderAppConfig
          .from(Env(Map("URL_DOWNLOADER_ORDER" -> "newest")), defaults)
          .exit
          .map { exit =>
            assertTrue(exit match
              case Exit.Failure(cause) =>
                cause.failureOption.exists {
                  case error: AppError.Config => error.detail.contains("asc or desc")
                  case _                      => false
                }
              case _ => false
            )
          }
      }
    )
