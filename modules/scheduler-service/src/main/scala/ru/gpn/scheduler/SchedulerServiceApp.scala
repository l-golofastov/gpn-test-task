package ru.gpn.scheduler

import ru.gpn.common.Env
import ru.gpn.common.db.{Database, HikariDatabase, Migrations}
import ru.gpn.common.http.SimpleHttpServer
import zio.*

object SchedulerServiceApp extends ZIOAppDefault:
  override def run: ZIO[Any, Any, Any] =
    for
      env <- Env.live
      config <- SchedulerConfig.fromEnv(env)
      _ <- ZIO.logInfo(
        s"Starting scheduler-service on ${config.http.host}:${config.http.port}, " +
          s"generate=${config.generateIntervalMillis}ms, snapshot=${config.snapshotIntervalMillis}ms"
      )
      _ <- ZIO.scoped(runWithDatabase(config).provideSomeLayer[Scope](HikariDatabase.layer(config.db)))
    yield ()

  private def runWithDatabase(config: SchedulerConfig): ZIO[Database & Scope, Any, Unit] =
    for
      _ <- Migrations.runResource("db/scheduler-service.sql")
      database <- ZIO.service[Database]
      repository = JdbcWasteRepository(database)
      scheduler <- WasteScheduler.make(repository, config)
      _ <- scheduler.start
      _ <- SimpleHttpServer.serve(config.http, SchedulerRoutes.router(scheduler, repository, config))
    yield ()
