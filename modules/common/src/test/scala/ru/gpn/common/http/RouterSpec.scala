package ru.gpn.common.http

import zio.test.*

object RouterSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment, Any] =
    suite("Router")(
      test("matches path params") {
        val router = Router(List(
          Route("GET", "/items/:id", request => request.pathParam("id").map(id => HttpResponse.text(id)))
        ))

        for response <- router.handle(HttpRequest("GET", List("items", "42"), Map.empty, Map.empty, ""))
        yield assertTrue(response.status == 200, response.body == "42")
      },
      test("returns 404 for unknown routes") {
        val router = Router.empty

        for response <- router.handle(HttpRequest("GET", List("missing"), Map.empty, Map.empty, ""))
        yield assertTrue(response.status == 404, response.body.contains("not_found"))
      }
    )
