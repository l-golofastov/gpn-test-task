package ru.gpn.scheduler

import ru.gpn.common.db.DbConfig
import ru.gpn.common.http.{HttpRequest, Router, ServerConfig}
import zio.*
import zio.json.*
import zio.test.*

object SchedulerRoutesSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("SchedulerRoutes")(
      test("health endpoint returns ok") {
        for
          setup <- makeSetup
          response <- setup.router.handle(request("GET", "/health"))
        yield assertTrue(
          response.status == 200,
          response.body.contains("\"status\":\"ok\"")
        )
      },
      test("manual both tick generates records and saves snapshot") {
        for
          setup <- makeSetup
          response <- setup.router.handle(request("POST", "/tick", Map("mode" -> List("both"))))
          body <- parseJson[ManualTickResponse](response.body)
          counts <- setup.repository.tableCounts
        yield assertTrue(
          response.status == 200,
          body.mode == "both",
          body.generated.exists(_.totalInserted == WasteType.all.size),
          body.snapshot.exists(_.totals.total > BigDecimal(0)),
          WasteType.all.forall(wasteType => counts.get(wasteType).contains(1L))
        )
      },
      test("invalid manual tick mode returns validation error") {
        for
          setup <- makeSetup
          response <- setup.router.handle(request("POST", "/tick", Map("mode" -> List("bad"))))
        yield assertTrue(
          response.status == 400,
          response.body.contains("Unsupported tick mode")
        )
      },
      test("stats endpoint returns totals and snapshots") {
        for
          setup <- makeSetup
          _ <- setup.scheduler.generateTick
          _ <- setup.scheduler.snapshotTick
          response <- setup.router.handle(request("GET", "/stats", Map("limit" -> List("1"))))
          body <- parseJson[StatsResponse](response.body)
        yield assertTrue(
          response.status == 200,
          body.totals.total > BigDecimal(0),
          body.latestSnapshot.nonEmpty,
          body.snapshots.size == 1
        )
      }
    )

  private final case class RouteSetup(
      repository: InMemoryWasteRepository,
      scheduler: WasteScheduler,
      router: Router
  )

  private def makeSetup: UIO[RouteSetup] =
    for
      repository <- InMemoryWasteRepository.make
      scheduler <- WasteScheduler.make(repository, testConfig)
      router = SchedulerRoutes.router(scheduler, repository, testConfig)
    yield RouteSetup(repository, scheduler, router)

  private val testConfig: SchedulerConfig =
    SchedulerConfig(
      http = ServerConfig("127.0.0.1", 0),
      db = DbConfig.postgres("jdbc:postgresql://localhost/scheduler_test", "postgres", "postgres"),
      generateInterval = Duration.fromMillis(10),
      snapshotInterval = Duration.fromMillis(20),
      itemsPerType = 1,
      statsLimit = 5
    )

  private def request(method: String, path: String, query: Map[String, List[String]] = Map.empty): HttpRequest =
    HttpRequest(
      method = method,
      path = path.stripPrefix("/").split('/').toList.filter(_.nonEmpty),
      query = query,
      headers = Map.empty,
      body = ""
    )

  private def parseJson[A: JsonDecoder](body: String): Task[A] =
    ZIO.fromEither(body.fromJson[A]).mapError(error => RuntimeException(error))
