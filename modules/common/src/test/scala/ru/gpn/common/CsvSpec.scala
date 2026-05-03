package ru.gpn.common

import zio.test.*

object CsvSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment, Any] =
    suite("Csv")(
      test("renders and parses quoted values") {
        val csv = Csv.render(
          headers = List("name", "password", "comment"),
          rows = List(List("Gov,Services", "a\"b", "needs, quote"))
        )

        val parsed = Csv.parse(csv).toOption.get
        assertTrue(
          parsed.head("name") == "Gov,Services",
          parsed.head("password") == "a\"b",
          parsed.head("comment") == "needs, quote"
        )
      },
      test("rejects unclosed quoted values") {
        assertTrue(Csv.parse("name\n\"broken").isLeft)
      }
    )
