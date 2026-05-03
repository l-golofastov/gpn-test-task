package ru.gpn.password

import zio.test.*

object PasswordValidationSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment, Any] =
    suite("PasswordValidation")(
      test("normalizes valid create requests") {
        for
          result <- PasswordValidation.create(CreatePasswordRequest("  mail  ", "secret", Some("  prod  ")))
        yield assertTrue(
          result == NewPassword("mail", "secret", "prod")
        )
      },
      test("rejects empty name and password") {
        for
          emptyName <- PasswordValidation.create(CreatePasswordRequest(" ", "secret", None)).exit
          emptyPassword <- PasswordValidation.create(CreatePasswordRequest("mail", "", None)).exit
        yield assertTrue(emptyName.isFailure, emptyPassword.isFailure)
      },
      test("requires at least one patch field") {
        for
          emptyPatch <- PasswordValidation.patch(UpdatePasswordRequest()).exit
          passwordPatch <- PasswordValidation.patch(UpdatePasswordRequest(password = Some("new")))
        yield assertTrue(
          emptyPatch.isFailure,
          passwordPatch == PasswordPatch(None, Some("new"), None)
        )
      }
    )
