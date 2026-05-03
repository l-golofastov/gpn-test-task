package ru.gpn.password

import zio.test.*

import java.util.UUID

object PasswordCsvSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment, Any] =
    suite("PasswordCsv")(
      test("parses valid rows and reports invalid rows") {
        val csv =
          """name,password,comment
            |mail,secret,primary
            |,missing-name,bad
            |docs,docs-secret,"with, comma"
            |""".stripMargin

        val result = PasswordCsv.parseImport(csv).toOption.get

        assertTrue(
          result.records == List(
            NewPassword("mail", "secret", "primary"),
            NewPassword("docs", "docs-secret", "with, comma")
          ),
          result.errors == List("Row 3: Name must not be empty")
        )
      },
      test("exports records with stable headers and escaping") {
        val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val csv = PasswordCsv.render(
          List(
            PasswordRecord(
              id = id,
              name = "mail",
              password = "sec,ret",
              comment = "quoted \"value\"",
              created = 10L,
              deleted = Some(20L)
            )
          )
        )

        assertTrue(
          csv ==
            "id,name,password,comment,created,deleted\n" +
              "11111111-1111-1111-1111-111111111111,mail,\"sec,ret\",\"quoted \"\"value\"\"\",10,20\n"
        )
      }
    )
