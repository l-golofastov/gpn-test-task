package ru.gpn.downloader

object DownloaderOpenApi:
  val json: String =
    """{
      |  "openapi": "3.0.3",
      |  "info": {
      |    "title": "URL Downloader Service",
      |    "version": "0.1.0",
      |    "description": "Asynchronous StackOverflow search downloader by tags"
      |  },
      |  "paths": {
      |    "/health": {
      |      "get": {
      |        "summary": "Health check",
      |        "responses": {
      |          "200": {
      |            "description": "Service is alive"
      |          }
      |        }
      |      }
      |    },
      |    "/downloads": {
      |      "post": {
      |        "summary": "Start asynchronous download",
      |        "parameters": [
      |          {
      |            "name": "tagged",
      |            "in": "query",
      |            "required": true,
      |            "schema": {
      |              "type": "array",
      |              "items": { "type": "string" }
      |            },
      |            "style": "form",
      |            "explode": true,
      |            "description": "Tags can be repeated or comma-separated"
      |          }
      |        ],
      |        "responses": {
      |          "202": {
      |            "description": "Download job accepted"
      |          },
      |          "400": {
      |            "description": "Invalid tag input"
      |          }
      |        }
      |      }
      |    },
      |    "/downloads/{jobId}": {
      |      "get": {
      |        "summary": "Get download job status",
      |        "parameters": [
      |          {
      |            "name": "jobId",
      |            "in": "path",
      |            "required": true,
      |            "schema": { "type": "string" }
      |          }
      |        ],
      |        "responses": {
      |          "200": {
      |            "description": "Job view"
      |          },
      |          "404": {
      |            "description": "Job not found"
      |          }
      |        }
      |      }
      |    }
      |  }
      |}""".stripMargin

  val swaggerHtml: String =
    """<!doctype html>
      |<html>
      |<head>
      |  <meta charset="utf-8">
      |  <title>URL Downloader API</title>
      |  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
      |</head>
      |<body>
      |  <div id="swagger-ui"></div>
      |  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
      |  <script>
      |    SwaggerUIBundle({ url: "/openapi.json", dom_id: "#swagger-ui" });
      |  </script>
      |</body>
      |</html>""".stripMargin
