package ru.gpn.password

import ru.gpn.common.http.HttpRequest
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

object PasswordRoutesSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment, Any] =
    suite("PasswordRoutes")(
      test("handles create, update, search, history and soft delete") {
        for
          repository <- InMemoryPasswordRepository.make
          router = PasswordRoutes.router(repository)
          createdResponse <- router.handle(
            request(
              method = "POST",
              path = List("api", "passwords"),
              body = """{"name":"mail","password":"secret","comment":"primary"}"""
            )
          )
          created <- decodeBody[PasswordRecord](createdResponse.body)
          updatedResponse <- router.handle(
            request(
              method = "PUT",
              path = List("api", "passwords", created.id.toString),
              body = """{"password":"rotated"}"""
            )
          )
          updated <- decodeBody[PasswordRecord](updatedResponse.body)
          historyResponse <- router.handle(request("GET", List("api", "passwords", created.id.toString, "history")))
          history <- decodeBody[List[PasswordHistoryEntry]](historyResponse.body)
          searchResponse <- router.handle(
            request(
              method = "GET",
              path = List("api", "passwords", "search"),
              query = Map("q" -> List("rotated"), "field" -> List("password"), "mode" -> List("exact"))
            )
          )
          search <- decodeBody[List[PasswordRecord]](searchResponse.body)
          deleteResponse <- router.handle(request("DELETE", List("api", "passwords", created.id.toString)))
          getDeletedResponse <- router.handle(request("GET", List("api", "passwords", created.id.toString)))
        yield assertTrue(
          createdResponse.status == 201,
          created.name == "mail",
          updatedResponse.status == 200,
          updated.password == "rotated",
          historyResponse.status == 200,
          history.map(_.password) == List("secret", "rotated"),
          searchResponse.status == 200,
          search.map(_.id) == List(created.id),
          deleteResponse.status == 204,
          getDeletedResponse.status == 404
        )
      },
      test("returns OpenAPI JSON") {
        for
          repository <- InMemoryPasswordRepository.make
          response <- PasswordRoutes.router(repository).handle(request("GET", List("openapi.json")))
        yield assertTrue(
          response.status == 200,
          response.contentType.startsWith("application/json"),
          PasswordOpenApi.json.fromJson[Json].isRight,
          response.body.contains("\"openapi\""),
          response.body.contains("/api/passwords/{id}/history")
        )
      }
    )

  private def request(
      method: String,
      path: List[String],
      query: Map[String, List[String]] = Map.empty,
      body: String = ""
  ): HttpRequest =
    HttpRequest(
      method = method,
      path = path,
      query = query,
      headers = Map.empty,
      body = body
    )

  private def decodeBody[A: JsonDecoder](body: String): Task[A] =
    ZIO.fromEither(body.fromJson[A]).mapError(error => new RuntimeException(error))
