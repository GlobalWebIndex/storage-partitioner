import java.util.TimeZone

import Dependencies._
import Deploy._

crossScalaVersions in ThisBuild := Seq("2.12.6", "2.11.8")
organization in ThisBuild := "net.globalwebindex"
libraryDependencies in ThisBuild ++= Seq(monix, akkaSlf4j, akkaActor, akkaStream, scalatest, scalameter, loggingImplLogback % "test") ++ jodaTime ++ loggingApi
resolvers in ThisBuild ++= Seq(
  "Maven Central Google Mirror EU" at "https://maven-central-eu.storage-download.googleapis.com/repos/central/data/",
  Resolver.bintrayRepo("l15k4", "GlobalWebIndex")
)
version in ThisBuild ~= (_.replace('+', '-'))
dynver in ThisBuild ~= (_.replace('+', '-'))
cancelable in ThisBuild := true
publishArtifact in ThisBuild := false

initialize := {
  System.setProperty("user.timezone", "UTC")
  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
}

lazy val root = (project in file("."))
  .aggregate(`storage-partitioner-api`, `storage-partitioner-s3`, `storage-partitioner-cql`, `storage-partitioner-druid`, `storage-partitioner-gcs`, `storage-partitioner-gcs`, `storage-partitioner-all`)
  .settings(skip in publish := true)
  .settings(skip in update := true)

lazy val `storage-partitioner-api` = (project in file("src/api"))
  .settings(bintraySettings("GlobalWebIndex", "storage-partitioner"))
  .settings(libraryDependencies += druid4sUtils)

lazy val `storage-partitioner-s3` = (project in file("src/core/s3"))
  .settings(libraryDependencies ++= Seq(alpakkaS3, s3mock, awsS3 % "test"))
  .settings(bintraySettings("GlobalWebIndex", "storage-partitioner"))
  .dependsOn(`storage-partitioner-api` % "compile->compile;test->test")

lazy val `storage-partitioner-cql` = (project in file("src/core/cql"))
  .settings(libraryDependencies ++= cassandraDeps :+ alpakkaCassandra)
  .settings(bintraySettings("GlobalWebIndex", "storage-partitioner"))
  .dependsOn(`storage-partitioner-api` % "compile->compile;test->test")

lazy val `storage-partitioner-druid` = (project in file("src/core/druid"))
  .dependsOn(`storage-partitioner-api` % "compile->compile;test->test")
  .settings(bintraySettings("GlobalWebIndex", "storage-partitioner"))
  .settings(libraryDependencies += druid4sClient)

lazy val `storage-partitioner-gcs` = (project in file("src/core/gcs"))
  .settings(libraryDependencies ++= Seq(alpakkaGCS))
  .settings(bintraySettings("GlobalWebIndex", "storage-partitioner"))
  .dependsOn(
    `storage-partitioner-s3` % "compile->compile;test->test",
    `storage-partitioner-api` % "compile->compile;test->test"
  )

lazy val `storage-partitioner-all` = (project in file("src/all"))
  .settings(libraryDependencies += sprayJson)
  .settings(bintraySettings("GlobalWebIndex", "storage-partitioner"))
  .dependsOn(
    `storage-partitioner-api` % "compile->compile;test->test",
    `storage-partitioner-s3` % "compile->compile;test->test",
    `storage-partitioner-druid` % "compile->compile;test->test",
    `storage-partitioner-cql` % "compile->compile;test->test",
    `storage-partitioner-gcs` % "compile->compile;test->test"
  )
