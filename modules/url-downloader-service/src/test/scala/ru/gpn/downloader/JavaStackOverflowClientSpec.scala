package ru.gpn.downloader

import zio.*
import zio.test.*

import java.io.{BufferedReader, InputStreamReader}
import java.net.{InetAddress, ServerSocket, Socket}
import java.net.http.HttpClient as JHttpClient
import java.nio.charset.StandardCharsets
import java.time.Duration as JDuration
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

object JavaStackOverflowClientSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("JavaStackOverflowClient")(
      test("calls configured StackExchange-compatible search endpoint") {
        ZIO.scoped {
          for
            server <- StubHttpServer.start("""{"items":[]}""")
            client = new JavaStackOverflowClient(
              config(s"http://127.0.0.1:${server.port}"),
              JHttpClient.newBuilder().connectTimeout(JDuration.ofSeconds(1)).version(JHttpClient.Version.HTTP_1_1).build()
            )
            body <- client.search("c#", 3)
            requestLine <- server.requestLine
          yield assertTrue(
            body == """{"items":[]}""",
            requestLine.startsWith("GET /search?"),
            requestLine.contains("page=3"),
            requestLine.contains("pagesize=10"),
            requestLine.contains("order=desc"),
            requestLine.contains("sort=activity"),
            requestLine.contains("tagged=c%23"),
            requestLine.contains("site=stackoverflow")
          )
        }
      },
      test("maps non-success HTTP status to external error") {
        ZIO.scoped {
          for
            server <- StubHttpServer.start("""{"error":"boom"}""", status = 500)
            client = new JavaStackOverflowClient(
              config(s"http://127.0.0.1:${server.port}"),
              JHttpClient.newBuilder().connectTimeout(JDuration.ofSeconds(1)).version(JHttpClient.Version.HTTP_1_1).build()
            )
            exit <- client.search("scala", 1).exit
          yield assertTrue(exit.isFailure)
        }
      }
    )

  private def config(baseUrl: String): DownloaderConfig =
    DownloaderConfig(
      pages = 1,
      pageSize = 10,
      sort = "activity",
      order = "desc",
      outputPath = java.nio.file.Paths.get("/tmp/url-downloader-client"),
      maxParallelTags = 1,
      stackExchangeBaseUrl = baseUrl,
      requestTimeout = JDuration.ofSeconds(1)
    )

private final class StubHttpServer private (
    server: ServerSocket,
    requests: LinkedBlockingQueue[String],
    thread: Thread
):
  val port: Int = server.getLocalPort

  def requestLine: Task[String] =
    ZIO.attemptBlocking {
      val line = requests.poll(2, TimeUnit.SECONDS)
      if line == null then throw RuntimeException("stub server did not receive a request")
      else line
    }

  def close: UIO[Unit] =
    ZIO.attempt(server.close()).orDie *> ZIO.attempt(thread.join(1000)).orDie.unit

private object StubHttpServer:
  def start(body: String, status: Int = 200): ZIO[Scope, Throwable, StubHttpServer] =
    ZIO.acquireRelease {
      ZIO.attempt {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val requests = LinkedBlockingQueue[String]()
        val thread = Thread(() => handleOne(server, requests, body, status), "url-downloader-stub-http")
        thread.setDaemon(true)
        thread.start()
        new StubHttpServer(server, requests, thread)
      }
    }(_.close)

  private def handleOne(server: ServerSocket, requests: LinkedBlockingQueue[String], body: String, status: Int): Unit =
    try
      val socket = server.accept()
      try
        val reader = BufferedReader(InputStreamReader(socket.getInputStream, StandardCharsets.UTF_8))
        val firstLine = reader.readLine()
        if firstLine != null then requests.offer(firstLine)
        var line = reader.readLine()
        while line != null && line.nonEmpty do line = reader.readLine()

        val bytes = body.getBytes(StandardCharsets.UTF_8)
        val reason = if status >= 200 && status < 300 then "OK" else "Error"
        val headers =
          s"HTTP/1.1 $status $reason\r\nContent-Type: application/json\r\nContent-Length: ${bytes.length}\r\nConnection: close\r\n\r\n"
        val output = socket.getOutputStream
        output.write(headers.getBytes(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
      finally socket.close()
    catch
      case _: Throwable => ()
