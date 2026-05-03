package ru.gpn.scheduler

import ru.gpn.common.AppError
import ru.gpn.common.db.{Database, DbConfig}
import zio.*
import zio.json.*

import java.sql.{Connection, PreparedStatement, ResultSet, Statement, Timestamp}
import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.util.Using

final case class JdbcWasteRepository(database: Database) extends WasteRepository:
  override def insertAll(wasteType: WasteType, entries: Chunk[WasteEntry]): IO[AppError, Int] =
    if entries.isEmpty then ZIO.succeed(0)
    else
      database
        .transaction { connection =>
          val sql =
            s"INSERT INTO ${wasteType.tableName} (id, weight_kg, created_at, metadata) VALUES (?, ?, ?, ?::jsonb)"
          Using.resource(connection.prepareStatement(sql)) { statement =>
            entries.foreach(entry => addWasteEntry(statement, entry))
            statement.executeBatch().map(insertedRows).sum
          }
        }
        .mapError(DbConfig.mapError)

  override def totalWeights: IO[AppError, Map[WasteType, BigDecimal]] =
    database
      .read { connection =>
        WasteType.all.map { wasteType =>
          wasteType -> queryBigDecimal(
            connection,
            s"SELECT COALESCE(SUM(weight_kg), 0) AS total_weight FROM ${wasteType.tableName}",
            "total_weight"
          )
        }.toMap
      }
      .mapError(DbConfig.mapError)

  override def saveSnapshot(snapshot: WasteSnapshot): IO[AppError, Unit] =
    database
      .transaction { connection =>
        val sql =
          """INSERT INTO waste_weight_snapshots
            |  (id, captured_at, plastic_weight, glass_weight, paper_weight, other_weight, total_weight, totals)
            |VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            |""".stripMargin
        Using.resource(connection.prepareStatement(sql)) { statement =>
          statement.setObject(1, snapshot.id)
          statement.setTimestamp(2, Timestamp.from(snapshot.capturedAt))
          statement.setBigDecimal(3, snapshot.totals.plastic.bigDecimal)
          statement.setBigDecimal(4, snapshot.totals.glass.bigDecimal)
          statement.setBigDecimal(5, snapshot.totals.paper.bigDecimal)
          statement.setBigDecimal(6, snapshot.totals.other.bigDecimal)
          statement.setBigDecimal(7, snapshot.totals.total.bigDecimal)
          statement.setString(8, snapshot.totals.toJson)
          statement.executeUpdate()
          ()
        }
      }
      .mapError(DbConfig.mapError)

  override def latestSnapshot: IO[AppError, Option[WasteSnapshot]] =
    listSnapshots(1).map(_.headOption)

  override def listSnapshots(limit: Int): IO[AppError, List[WasteSnapshot]] =
    database
      .read { connection =>
        val sql =
          """SELECT id, captured_at, plastic_weight, glass_weight, paper_weight, other_weight, total_weight
            |FROM waste_weight_snapshots
            |ORDER BY captured_at DESC
            |LIMIT ?
            |""".stripMargin
        Using.resource(connection.prepareStatement(sql)) { statement =>
          statement.setInt(1, limit)
          Using.resource(statement.executeQuery()) { resultSet =>
            val snapshots = ListBuffer.empty[WasteSnapshot]
            while resultSet.next() do snapshots += readSnapshot(resultSet)
            snapshots.toList
          }
        }
      }
      .mapError(DbConfig.mapError)

  override def tableCounts: IO[AppError, Map[WasteType, Long]] =
    database
      .read { connection =>
        WasteType.all.map { wasteType =>
          wasteType -> queryLong(connection, s"SELECT COUNT(*) AS total_rows FROM ${wasteType.tableName}", "total_rows")
        }.toMap
      }
      .mapError(DbConfig.mapError)

  private def addWasteEntry(statement: PreparedStatement, entry: WasteEntry): Unit =
    statement.setObject(1, entry.id)
    statement.setBigDecimal(2, entry.weightKg.bigDecimal)
    statement.setTimestamp(3, Timestamp.from(entry.createdAt))
    statement.setString(4, entry.metadata.toJson)
    statement.addBatch()

  private def queryBigDecimal(connection: Connection, sql: String, column: String): BigDecimal =
    Using.resource(connection.createStatement()) { statement =>
      Using.resource(statement.executeQuery(sql)) { resultSet =>
        if resultSet.next() then nullableBigDecimal(resultSet, column)
        else BigDecimal(0)
      }
    }

  private def queryLong(connection: Connection, sql: String, column: String): Long =
    Using.resource(connection.createStatement()) { statement =>
      Using.resource(statement.executeQuery(sql)) { resultSet =>
        if resultSet.next() then resultSet.getLong(column)
        else 0L
      }
    }

  private def readSnapshot(resultSet: ResultSet): WasteSnapshot =
    WasteSnapshot(
      id = resultSet.getObject("id", classOf[UUID]),
      capturedAt = resultSet.getTimestamp("captured_at").toInstant,
      totals = WasteTotals.fromValues(
        plastic = nullableBigDecimal(resultSet, "plastic_weight"),
        glass = nullableBigDecimal(resultSet, "glass_weight"),
        paper = nullableBigDecimal(resultSet, "paper_weight"),
        other = nullableBigDecimal(resultSet, "other_weight")
      )
    )

  private def nullableBigDecimal(resultSet: ResultSet, column: String): BigDecimal =
    Option(resultSet.getBigDecimal(column)).map(BigDecimal(_)).getOrElse(BigDecimal(0))

  private def insertedRows(value: Int): Int =
    value match
      case count if count == Statement.SUCCESS_NO_INFO => 1
      case count if count > 0                          => count
      case _                                           => 0
