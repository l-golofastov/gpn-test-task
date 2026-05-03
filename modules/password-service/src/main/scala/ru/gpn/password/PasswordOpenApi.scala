package ru.gpn.password

object PasswordOpenApi:
  val json: String =
    """
      {
        "openapi": "3.0.3",
        "info": {
          "title": "Password Service API",
          "version": "1.0.0",
          "description": "REST API for password records, search, CSV import/export, password history and statistics."
        },
        "servers": [
          {
            "url": "/"
          }
        ],
        "paths": {
          "/health": {
            "get": {
              "summary": "Health check",
              "responses": {
                "200": {
                  "description": "Service is healthy",
                  "content": {
                    "application/json": {
                      "schema": {
                        "$ref": "#/components/schemas/HealthResponse"
                      }
                    }
                  }
                }
              }
            }
          },
          "/api/passwords": {
            "get": {
              "summary": "List password records",
              "parameters": [
                {
                  "$ref": "#/components/parameters/includeDeleted"
                },
                {
                  "$ref": "#/components/parameters/limit"
                },
                {
                  "$ref": "#/components/parameters/offset"
                }
              ],
              "responses": {
                "200": {
                  "description": "Password records",
                  "content": {
                    "application/json": {
                      "schema": {
                        "type": "array",
                        "items": {
                          "$ref": "#/components/schemas/PasswordRecord"
                        }
                      }
                    }
                  }
                }
              }
            },
            "post": {
              "summary": "Create password record",
              "requestBody": {
                "required": true,
                "content": {
                  "application/json": {
                    "schema": {
                      "$ref": "#/components/schemas/CreatePasswordRequest"
                    }
                  }
                }
              },
              "responses": {
                "201": {
                  "description": "Created record",
                  "content": {
                    "application/json": {
                      "schema": {
                        "$ref": "#/components/schemas/PasswordRecord"
                      }
                    }
                  }
                }
              }
            }
          },
          "/api/passwords/{id}": {
            "get": {
              "summary": "Get password record",
              "parameters": [
                {
                  "$ref": "#/components/parameters/id"
                },
                {
                  "$ref": "#/components/parameters/includeDeleted"
                }
              ],
              "responses": {
                "200": {
                  "description": "Password record",
                  "content": {
                    "application/json": {
                      "schema": {
                        "$ref": "#/components/schemas/PasswordRecord"
                      }
                    }
                  }
                },
                "404": {
                  "$ref": "#/components/responses/Error"
                }
              }
            },
            "put": {
              "summary": "Update password record",
              "parameters": [
                {
                  "$ref": "#/components/parameters/id"
                }
              ],
              "requestBody": {
                "required": true,
                "content": {
                  "application/json": {
                    "schema": {
                      "$ref": "#/components/schemas/UpdatePasswordRequest"
                    }
                  }
                }
              },
              "responses": {
                "200": {
                  "description": "Updated record",
                  "content": {
                    "application/json": {
                      "schema": {
                        "$ref": "#/components/schemas/PasswordRecord"
                      }
                    }
                  }
                },
                "404": {
                  "$ref": "#/components/responses/Error"
                }
              }
            },
            "delete": {
              "summary": "Soft delete password record",
              "parameters": [
                {
                  "$ref": "#/components/parameters/id"
                }
              ],
              "responses": {
                "204": {
                  "description": "Deleted"
                },
                "404": {
                  "$ref": "#/components/responses/Error"
                }
              }
            }
          },
          "/api/passwords/search": {
            "get": {
              "summary": "Search password records by exact or partial match",
              "parameters": [
                {
                  "name": "q",
                  "in": "query",
                  "required": true,
                  "schema": {
                    "type": "string"
                  }
                },
                {
                  "name": "field",
                  "in": "query",
                  "schema": {
                    "type": "string",
                    "enum": ["any", "name", "password", "comment"],
                    "default": "any"
                  }
                },
                {
                  "name": "mode",
                  "in": "query",
                  "schema": {
                    "type": "string",
                    "enum": ["partial", "exact"],
                    "default": "partial"
                  }
                },
                {
                  "$ref": "#/components/parameters/includeDeleted"
                },
                {
                  "$ref": "#/components/parameters/limit"
                },
                {
                  "$ref": "#/components/parameters/offset"
                }
              ],
              "responses": {
                "200": {
                  "description": "Search result",
                  "content": {
                    "application/json": {
                      "schema": {
                        "type": "array",
                        "items": {
                          "$ref": "#/components/schemas/PasswordRecord"
                        }
                      }
                    }
                  }
                }
              }
            }
          },
          "/api/passwords/{id}/history": {
            "get": {
              "summary": "Get password change history",
              "parameters": [
                {
                  "$ref": "#/components/parameters/id"
                }
              ],
              "responses": {
                "200": {
                  "description": "Password history",
                  "content": {
                    "application/json": {
                      "schema": {
                        "type": "array",
                        "items": {
                          "$ref": "#/components/schemas/PasswordHistoryEntry"
                        }
                      }
                    }
                  }
                },
                "404": {
                  "$ref": "#/components/responses/Error"
                }
              }
            }
          },
          "/api/passwords/export": {
            "get": {
              "summary": "Export password records to CSV",
              "parameters": [
                {
                  "$ref": "#/components/parameters/includeDeleted"
                }
              ],
              "responses": {
                "200": {
                  "description": "CSV export",
                  "content": {
                    "text/csv": {
                      "schema": {
                        "type": "string"
                      }
                    }
                  }
                }
              }
            }
          },
          "/api/passwords/import": {
            "post": {
              "summary": "Import password records from CSV",
              "requestBody": {
                "required": true,
                "content": {
                  "text/csv": {
                    "schema": {
                      "type": "string"
                    }
                  }
                }
              },
              "responses": {
                "200": {
                  "description": "Import result",
                  "content": {
                    "application/json": {
                      "schema": {
                        "$ref": "#/components/schemas/CsvImportResult"
                      }
                    }
                  }
                }
              }
            }
          },
          "/api/passwords/stats": {
            "get": {
              "summary": "Get password statistics",
              "parameters": [
                {
                  "name": "oldDays",
                  "in": "query",
                  "schema": {
                    "type": "integer",
                    "default": 365,
                    "minimum": 0
                  }
                },
                {
                  "name": "oldestLimit",
                  "in": "query",
                  "schema": {
                    "type": "integer",
                    "default": 20,
                    "minimum": 1
                  }
                }
              ],
              "responses": {
                "200": {
                  "description": "Password statistics",
                  "content": {
                    "application/json": {
                      "schema": {
                        "$ref": "#/components/schemas/PasswordStats"
                      }
                    }
                  }
                }
              }
            }
          }
        },
        "components": {
          "parameters": {
            "id": {
              "name": "id",
              "in": "path",
              "required": true,
              "schema": {
                "type": "string",
                "format": "uuid"
              }
            },
            "includeDeleted": {
              "name": "includeDeleted",
              "in": "query",
              "schema": {
                "type": "boolean",
                "default": false
              }
            },
            "limit": {
              "name": "limit",
              "in": "query",
              "schema": {
                "type": "integer",
                "default": 100,
                "minimum": 1,
                "maximum": 1000
              }
            },
            "offset": {
              "name": "offset",
              "in": "query",
              "schema": {
                "type": "integer",
                "default": 0,
                "minimum": 0
              }
            }
          },
          "responses": {
            "Error": {
              "description": "Error response",
              "content": {
                "application/json": {
                  "schema": {
                    "$ref": "#/components/schemas/ErrorResponse"
                  }
                }
              }
            }
          },
          "schemas": {
            "HealthResponse": {
              "type": "object",
              "required": ["status"],
              "properties": {
                "status": {
                  "type": "string"
                }
              }
            },
            "PasswordRecord": {
              "type": "object",
              "required": ["id", "name", "password", "comment", "created"],
              "properties": {
                "id": {
                  "type": "string",
                  "format": "uuid"
                },
                "name": {
                  "type": "string"
                },
                "password": {
                  "type": "string"
                },
                "comment": {
                  "type": "string"
                },
                "created": {
                  "type": "integer",
                  "format": "int64"
                },
                "deleted": {
                  "type": "integer",
                  "format": "int64",
                  "nullable": true
                }
              }
            },
            "CreatePasswordRequest": {
              "type": "object",
              "required": ["name", "password"],
              "properties": {
                "name": {
                  "type": "string"
                },
                "password": {
                  "type": "string"
                },
                "comment": {
                  "type": "string"
                }
              }
            },
            "UpdatePasswordRequest": {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string"
                },
                "password": {
                  "type": "string"
                },
                "comment": {
                  "type": "string"
                }
              }
            },
            "PasswordHistoryEntry": {
              "type": "object",
              "required": ["id", "password", "changed"],
              "properties": {
                "id": {
                  "type": "integer",
                  "format": "int64"
                },
                "password": {
                  "type": "string"
                },
                "changed": {
                  "type": "integer",
                  "format": "int64"
                }
              }
            },
            "PasswordStats": {
              "type": "object",
              "required": [
                "total",
                "active",
                "deleted",
                "uniquePasswords",
                "duplicatePasswordGroups",
                "duplicatePasswordEntries",
                "oldPasswords"
              ],
              "properties": {
                "total": {
                  "type": "integer",
                  "format": "int64"
                },
                "active": {
                  "type": "integer",
                  "format": "int64"
                },
                "deleted": {
                  "type": "integer",
                  "format": "int64"
                },
                "uniquePasswords": {
                  "type": "integer",
                  "format": "int64"
                },
                "duplicatePasswordGroups": {
                  "type": "integer",
                  "format": "int64"
                },
                "duplicatePasswordEntries": {
                  "type": "integer",
                  "format": "int64"
                },
                "oldPasswords": {
                  "type": "array",
                  "items": {
                    "$ref": "#/components/schemas/OldPasswordRecord"
                  }
                }
              }
            },
            "OldPasswordRecord": {
              "type": "object",
              "required": ["id", "name", "created", "lastChanged", "ageDays"],
              "properties": {
                "id": {
                  "type": "string",
                  "format": "uuid"
                },
                "name": {
                  "type": "string"
                },
                "created": {
                  "type": "integer",
                  "format": "int64"
                },
                "lastChanged": {
                  "type": "integer",
                  "format": "int64"
                },
                "ageDays": {
                  "type": "integer",
                  "format": "int64"
                }
              }
            },
            "CsvImportResult": {
              "type": "object",
              "required": ["imported", "failed", "errors"],
              "properties": {
                "imported": {
                  "type": "integer"
                },
                "failed": {
                  "type": "integer"
                },
                "errors": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                }
              }
            },
            "ErrorResponse": {
              "type": "object",
              "required": ["error", "message"],
              "properties": {
                "error": {
                  "type": "string"
                },
                "message": {
                  "type": "string"
                }
              }
            }
          }
        }
      }
    """.stripMargin

  val swaggerHtml: String =
    """
      <!doctype html>
      <html lang="en">
        <head>
          <meta charset="utf-8">
          <title>Password Service API</title>
          <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
        </head>
        <body>
          <div id="swagger-ui"></div>
          <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
          <script>
            window.onload = function() {
              window.ui = SwaggerUIBundle({
                url: "/openapi.json",
                dom_id: "#swagger-ui"
              });
            };
          </script>
        </body>
      </html>
    """.stripMargin
