ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "ru.gpn"
ThisBuild / scalaVersion := "3.3.7"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-encoding",
  "utf8"
)

ThisBuild / Test / fork := true
ThisBuild / Test / parallelExecution := false

val zioVersion = "2.1.19"
val zioJsonVersion = "0.7.44"
val hikariVersion = "6.3.0"
val postgresqlVersion = "42.7.5"
val testcontainersVersion = "1.20.4"

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "dev.zio" %% "zio" % zioVersion,
    "dev.zio" %% "zio-streams" % zioVersion,
    "dev.zio" %% "zio-json" % zioJsonVersion,
    "com.zaxxer" % "HikariCP" % hikariVersion,
    "org.postgresql" % "postgresql" % postgresqlVersion,
    "dev.zio" %% "zio-test" % zioVersion % Test,
    "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
    "org.testcontainers" % "postgresql" % testcontainersVersion % Test
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  Compile / run / fork := true,
  Compile / run / javaOptions += "--add-modules=jdk.httpserver",
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", xs @ _*) =>
      xs.map(_.toLowerCase) match {
        case "manifest.mf" :: Nil => MergeStrategy.discard
        case "services" :: _      => MergeStrategy.concat
        case _                    => MergeStrategy.discard
      }
    case _ => MergeStrategy.first
  }
)

lazy val root = (project in file("."))
  .aggregate(common, passwordService, urlDownloaderService, schedulerService)
  .settings(
    name := "gpn-test-task",
    publish / skip := true,
    assembly / skip := true
  )

lazy val common = (project in file("modules/common"))
  .settings(commonSettings)
  .settings(
    name := "common",
    assembly / skip := true
  )

lazy val passwordService = (project in file("modules/password-service"))
  .dependsOn(common % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "password-service",
    assembly / mainClass := Some("ru.gpn.password.PasswordServiceApp")
  )

lazy val urlDownloaderService = (project in file("modules/url-downloader-service"))
  .dependsOn(common % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "url-downloader-service",
    assembly / mainClass := Some("ru.gpn.downloader.UrlDownloaderApp")
  )

lazy val schedulerService = (project in file("modules/scheduler-service"))
  .dependsOn(common % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "scheduler-service",
    assembly / mainClass := Some("ru.gpn.scheduler.SchedulerServiceApp")
  )
