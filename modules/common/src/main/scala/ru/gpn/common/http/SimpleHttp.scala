package ru.gpn.common.http

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import ru.gpn.common.{AppError, ErrorResponse}
import zio.*
import zio.json.*

import java.net.{InetSocketAddress, URLDecoder}
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

final case class ServerConfig(host: String, port: Int)

final case class HttpRequest(
    method: String,
    path: List[String],
    query: Map[String, List[String]],
    headers: Map[String, List[String]],
    body: String,
    params: Map[String, String] = Map.empty
):
  def queryParam(name: String): Option[String] =
    query.get(name).flatMap(_.headOption)

  def requiredQueryParam(name: String): IO[AppError.Validation, String] =
    ZIO.fromOption(queryParam(name)).orElseFail(AppError.Validation(s"Missing query parameter: $name"))

  def pathParam(name: String): IO[AppError.Validation, String] =
    ZIO.fromOption(params.get(name)).orElseFail(AppError.Validation(s"Missing path parameter: $name"))

  def jsonBody[A: JsonDecoder]: IO[AppError.Validation, A] =
    ZIO.fromEither(body.fromJson[A]).mapError(error => AppError.Validation(s"Invalid JSON body: $error"))

final case class HttpResponse(
    status: Int,
    body: String,
    contentType: String = "application/json; charset=utf-8",
    headers: Map[String, String] = Map.empty
)

object HttpResponse:
  def json[A: JsonEncoder](value: A, status: Int = 200): HttpResponse =
    HttpResponse(status, value.toJson)

  def text(value: String, status: Int = 200, contentType: String = "text/plain; charset=utf-8"): HttpResponse =
    HttpResponse(status, value, contentType)

  def empty(status: Int): HttpResponse =
    HttpResponse(status, "")

final case class Route(
    method: String,
    path: String,
    handler: HttpRequest => IO[AppError, HttpResponse]
):
  private val pattern = PathPattern.parse(path)

  def matches(request: HttpRequest): Option[HttpRequest] =
    if method != request.method then None
    else pattern.matchPath(request.path).map(params => request.copy(params = params))

final case class Router(routes: List[Route]):
  def ++(other: Router): Router =
    Router(routes ++ other.routes)

  def handle(request: HttpRequest): UIO[HttpResponse] =
    if request.method == "OPTIONS" then ZIO.succeed(HttpResponse.empty(204))
    else
      routes.view.flatMap(route => route.matches(request).map(route -> _)).headOption match
        case None =>
          ZIO.succeed(errorResponse(AppError.NotFound(s"No route for ${request.method} /${request.path.mkString("/")}")))
        case Some((route, matchedRequest)) =>
          route
            .handler(matchedRequest)
            .catchAll(error => ZIO.succeed(errorResponse(error)))
            .catchAllDefect(error => ZIO.succeed(errorResponse(AppError.fromThrowable(error))))

  private def errorResponse(error: AppError): HttpResponse =
    HttpResponse.json(ErrorResponse(error.code, error.detail), error.status)

object Router:
  val empty: Router = Router(Nil)

object SimpleHttpServer:
  def serve(config: ServerConfig, router: Router): ZIO[Any with Scope, Throwable, Unit] =
    ZIO.runtime[Any].flatMap { runtime =>
      ZIO.acquireRelease {
        ZIO.attempt {
          val server = HttpServer.create(new InetSocketAddress(config.host, config.port), 0)
          server.createContext("/", exchange => handleExchange(runtime, router, exchange))
          server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
          server
        }
      } { server =>
        ZIO.attempt(server.stop(0)).orDie
      }.flatMap { server =>
        ZIO.attempt(server.start()) *>
          ZIO.logInfo(s"HTTP server started on ${config.host}:${config.port}") *>
          ZIO.never
      }
    }

  private def handleExchange(runtime: Runtime[Any], router: Router, exchange: HttpExchange): Unit =
    val response =
      try
        val request = requestFrom(exchange)
        Unsafe.unsafe { implicit unsafe =>
          runtime.unsafe.run(router.handle(request)).getOrThrowFiberFailure()
        }
      catch
        case error: Throwable =>
          HttpResponse.json(ErrorResponse("unexpected_error", Option(error.getMessage).getOrElse(error.toString)), 500)

    writeResponse(exchange, response)

  private def requestFrom(exchange: HttpExchange): HttpRequest =
    val uri = exchange.getRequestURI
    HttpRequest(
      method = exchange.getRequestMethod.toUpperCase,
      path = uri.getPath.split('/').toList.filter(_.nonEmpty).map(decode),
      query = parseQuery(Option(uri.getRawQuery).getOrElse("")),
      headers = exchange.getRequestHeaders.asScala.view.mapValues(_.asScala.toList).toMap,
      body = String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
    )

  private def writeResponse(exchange: HttpExchange, response: HttpResponse): Unit =
    val headers = exchange.getResponseHeaders
    headers.set("Content-Type", response.contentType)
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
    headers.set("Access-Control-Allow-Headers", "Content-Type")
    response.headers.foreach { case (name, value) => headers.set(name, value) }
    val bytes = response.body.getBytes(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(response.status, bytes.length.toLong)
    val os = exchange.getResponseBody
    try os.write(bytes)
    finally os.close()

  private def parseQuery(raw: String): Map[String, List[String]] =
    if raw.trim.isEmpty then Map.empty
    else
      raw
        .split("&")
        .toList
        .filter(_.nonEmpty)
        .map { pair =>
          pair.split("=", 2).toList match
            case key :: value :: Nil => decode(key) -> decode(value)
            case key :: Nil          => decode(key) -> ""
            case Nil                 => "" -> ""
            case key :: value :: _   => decode(key) -> decode(value)
        }
        .groupMap(_._1)(_._2)

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

private final case class PathPattern(parts: List[PathPart]):
  def matchPath(path: List[String]): Option[Map[String, String]] =
    if parts.length != path.length then None
    else
      parts.zip(path).foldLeft(Option(Map.empty[String, String])) {
        case (None, _) => None
        case (Some(params), (PathPart.Static(expected), actual)) if expected == actual =>
          Some(params)
        case (Some(params), (PathPart.Param(name), actual)) =>
          Some(params.updated(name, actual))
        case _ => None
      }

private object PathPattern:
  def parse(path: String): PathPattern =
    PathPattern(
      path
        .split('/')
        .toList
        .filter(_.nonEmpty)
        .map {
          case part if part.startsWith(":") => PathPart.Param(part.drop(1))
          case part                         => PathPart.Static(part)
        }
    )

private enum PathPart:
  case Static(value: String)
  case Param(name: String)
