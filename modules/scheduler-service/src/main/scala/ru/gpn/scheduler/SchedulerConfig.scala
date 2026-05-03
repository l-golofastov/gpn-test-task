package ru.gpn.scheduler

import ru.gpn.common.{AppError, Env}
import ru.gpn.common.db.DbConfig
import ru.gpn.common.http.ServerConfig
import zio.*

final case class SchedulerConfig(
    http: ServerConfig,
    db: DbConfig,
    generateInterval: Duration,
    snapshotInterval: Duration,
    itemsPerType: Int,
    statsLimit: Int
):
  def generateIntervalMillis: Long = generateInterval.toMillis
  def snapshotIntervalMillis: Long = snapshotInterval.toMillis

object SchedulerConfig:
  def fromEnv(env: Env): IO[AppError.Config, SchedulerConfig] =
    for
      httpPort <- env.positiveInt("SCHEDULER_HTTP_PORT", 8083)
      poolSize <- env.positiveInt("SCHEDULER_DB_POOL_SIZE", 8)
      generateIntervalMs <- env.positiveLong("SCHEDULER_GENERATE_INTERVAL_MS", 5000L)
      snapshotIntervalMs <- env.positiveLong("SCHEDULER_SNAPSHOT_INTERVAL_MS", 15000L)
      itemsPerType <- env.positiveInt("SCHEDULER_ITEMS_PER_TYPE", 10)
      statsLimit <- env.positiveInt("SCHEDULER_STATS_LIMIT", 10)
    yield SchedulerConfig(
      http = ServerConfig(
        host = env.string("SCHEDULER_HTTP_HOST", "0.0.0.0"),
        port = httpPort
      ),
      db = DbConfig.postgres(
        jdbcUrl = env.string(
          "SCHEDULER_DB_JDBC_URL",
          env.string("DB_JDBC_URL", "jdbc:postgresql://localhost:5432/gpn_scheduler")
        ),
        username = env.string("SCHEDULER_DB_USERNAME", env.string("DB_USERNAME", "postgres")),
        password = env.string("SCHEDULER_DB_PASSWORD", env.string("DB_PASSWORD", "postgres")),
        maximumPoolSize = poolSize
      ),
      generateInterval = Duration.fromMillis(generateIntervalMs),
      snapshotInterval = Duration.fromMillis(snapshotIntervalMs),
      itemsPerType = itemsPerType,
      statsLimit = statsLimit
    )
