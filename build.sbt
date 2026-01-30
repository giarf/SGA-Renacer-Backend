ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name := "SGA-Renacer-Backend", // Actualizamos el nombre según tu proyecto
    libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % "42.7.9",

      "com.typesafe.play" %% "play-json" % "2.10.8",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,

      "org.scalameta" %% "munit" % "1.2.2" % Test
    )
  )