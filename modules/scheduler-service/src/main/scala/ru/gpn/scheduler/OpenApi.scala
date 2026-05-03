package ru.gpn.scheduler

object OpenApi:
  val json: String =
    """{
      |  "openapi": "3.0.3",
      |  "info": {
      |    "title": "GPN Scheduler Service",
      |    "version": "0.1.0"
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
      |    "/status": {
      |      "get": {
      |        "summary": "Scheduler status and row counts",
      |        "responses": {
      |          "200": {
      |            "description": "Current scheduler state"
      |          }
      |        }
      |      }
      |    },
      |    "/tick": {
      |      "post": {
      |        "summary": "Run a manual scheduler tick",
      |        "parameters": [
      |          {
      |            "name": "mode",
      |            "in": "query",
      |            "required": false,
      |            "schema": {
      |              "type": "string",
      |              "enum": ["generate", "snapshot", "both"],
      |              "default": "both"
      |            }
      |          }
      |        ],
      |        "responses": {
      |          "200": {
      |            "description": "Manual tick result"
      |          },
      |          "400": {
      |            "description": "Invalid mode"
      |          }
      |        }
      |      }
      |    },
      |    "/stats": {
      |      "get": {
      |        "summary": "Current totals and saved snapshots",
      |        "parameters": [
      |          {
      |            "name": "limit",
      |            "in": "query",
      |            "required": false,
      |            "schema": {
      |              "type": "integer",
      |              "minimum": 1,
      |              "default": 10
      |            }
      |          }
      |        ],
      |        "responses": {
      |          "200": {
      |            "description": "Waste totals"
      |          }
      |        }
      |      }
      |    },
      |    "/openapi.json": {
      |      "get": {
      |        "summary": "OpenAPI document",
      |        "responses": {
      |          "200": {
      |            "description": "OpenAPI JSON"
      |          }
      |        }
      |      }
      |    }
      |  }
      |}""".stripMargin

  val swaggerHtml: String =
    """<!doctype html>
      |<html lang="en">
      |<head>
      |  <meta charset="utf-8">
      |  <title>GPN Scheduler Service API</title>
      |  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
      |</head>
      |<body>
      |  <div id="swagger-ui"></div>
      |  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
      |  <script>
      |    window.ui = SwaggerUIBundle({
      |      url: "/openapi.json",
      |      dom_id: "#swagger-ui"
      |    });
      |  </script>
      |</body>
      |</html>""".stripMargin
