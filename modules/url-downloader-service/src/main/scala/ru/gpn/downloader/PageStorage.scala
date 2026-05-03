package ru.gpn.downloader

import ru.gpn.common.AppError
import zio.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Path, StandardOpenOption}

trait PageStorage:
  def save(tag: String, page: Int, json: String): IO[AppError.FileSystem, Path]

final class FilePageStorage(root: Path) extends PageStorage:
  private val normalizedRoot = root.toAbsolutePath.normalize

  override def save(tag: String, page: Int, json: String): IO[AppError.FileSystem, Path] =
    val directory = normalizedRoot.resolve(TagDirectory.nameFor(tag))
    val file = directory.resolve(s"page-$page.json")
    ZIO
      .attemptBlocking {
        _root_.java.nio.file.Files.createDirectories(directory)
        _root_.java.nio.file.Files.writeString(
          file,
          json,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE
        )
      }
      .as(file.toAbsolutePath.normalize)
      .mapError(error => AppError.FileSystem(s"Cannot write ${file.toAbsolutePath.normalize}: ${error.getMessage}"))

object TagDirectory:
  def nameFor(tag: String): String =
    tag.flatMap { char =>
      if Character.isLetterOrDigit(char) || char == '-' || char == '_' || char == '.' then char.toString
      else f"_${char.toInt}%04X"
    }
