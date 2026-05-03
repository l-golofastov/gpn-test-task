package ru.gpn.downloader

import ru.gpn.common.AppError
import zio.*

import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient as JHttpClient, HttpRequest as JHttpRequest, HttpResponse as JHttpResponse}
import java.nio.charset.StandardCharsets

trait StackOverflowClient:
  def search(tag: String, page: Int): IO[AppError.External, String]

final class JavaStackOverflowClient(config: DownloaderConfig, client: JHttpClient) extends StackOverflowClient:
  override def search(tag: String, page: Int): IO[AppError.External, String] =
    for
      request <- ZIO.attempt(buildRequest(tag, page)).mapError(toExternal)
      response <- ZIO.attemptBlocking(client.send(request, JHttpResponse.BodyHandlers.ofString())).mapError(toExternal)
      body <- ZIO
        .succeed(response.body())
        .filterOrFail(_ => response.statusCode() >= 200 && response.statusCode() < 300) {
          AppError.External(s"StackOverflow API returned HTTP ${response.statusCode()}: ${response.body().take(200)}")
        }
    yield body

  private def buildRequest(tag: String, page: Int): JHttpRequest =
    JHttpRequest
      .newBuilder(searchUri(tag, page))
      .timeout(config.requestTimeout)
      .header("Accept", "application/json")
      .header("User-Agent", "gpn-url-downloader-service")
      .GET()
      .build()

  private def searchUri(tag: String, page: Int): URI =
    val query = List(
      "page" -> page.toString,
      "pagesize" -> config.pageSize.toString,
      "order" -> config.order,
      "sort" -> config.sort,
      "tagged" -> tag,
      "filter" -> "default",
      "site" -> "stackoverflow"
    ).map { case (name, value) => s"${encode(name)}=${encode(value)}" }.mkString("&")

    URI.create(s"${config.stackExchangeBaseUrl.stripSuffix("/")}/search?$query")

  private def encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

  private def toExternal(error: Throwable): AppError.External =
    AppError.External(s"StackOverflow API request failed: ${Option(error.getMessage).getOrElse(error.toString)}")

object JavaStackOverflowClient:
  def live(config: DownloaderConfig): JavaStackOverflowClient =
    val client = JHttpClient
      .newBuilder()
      .connectTimeout(config.requestTimeout)
      .build()
    new JavaStackOverflowClient(config, client)
