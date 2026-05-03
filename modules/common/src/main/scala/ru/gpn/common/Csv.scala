package ru.gpn.common

object Csv:
  def render(headers: List[String], rows: List[List[String]]): String =
    val allRows = headers :: rows
    allRows.map(_.map(escape).mkString(",")).mkString("\n") + "\n"

  def parse(input: String): Either[String, List[Map[String, String]]] =
    val rows = input
      .replace("\r\n", "\n")
      .replace('\r', '\n')
      .split('\n')
      .toList
      .filter(_.trim.nonEmpty)
      .map(parseLine)

    rows.collectFirst { case Left(error) => error } match
      case Some(error) => Left(error)
      case None =>
        rows.collect { case Right(row) => row } match
          case Nil => Right(Nil)
          case headers :: data =>
            val normalizedHeaders = headers.map(_.trim)
            if normalizedHeaders.exists(_.isEmpty) then Left("CSV header contains an empty column name")
            else
              Right(
                data.map { values =>
                  normalizedHeaders.zipAll(values, "", "").toMap
                }
              )

  private def parseLine(line: String): Either[String, List[String]] =
    val values = scala.collection.mutable.ListBuffer.empty[String]
    val current = new StringBuilder
    var quoted = false
    var i = 0
    while i < line.length do
      line.charAt(i) match
        case '"' if quoted && i + 1 < line.length && line.charAt(i + 1) == '"' =>
          current.append('"')
          i += 1
        case '"' =>
          quoted = !quoted
        case ',' if !quoted =>
          values += current.toString()
          current.clear()
        case char =>
          current.append(char)
      i += 1

    if quoted then Left(s"Unclosed quoted value in CSV line: $line")
    else
      values += current.toString()
      Right(values.toList)

  private def escape(value: String): String =
    val mustQuote = value.exists(ch => ch == ',' || ch == '"' || ch == '\n' || ch == '\r')
    val escaped = value.replace("\"", "\"\"")
    if mustQuote then s"\"$escaped\"" else escaped
