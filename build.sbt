import Dependencies._
import Deploy._

lazy val s3Resolver = "S3 Snapshots" at "s3://public.maven.globalwebindex.net.s3-eu-west-1.amazonaws.com/snapshots"

crossScalaVersions in ThisBuild := Seq("2.12.6", "2.11.8")
organization in ThisBuild := "net.globalwebindex"
libraryDependencies in ThisBuild ++= Seq(monix, akkaSlf4j, akkaActor, akkaStream, scalatest, scalameter, loggingImplLogback % "test") ++ jodaTime ++ loggingApi
resolvers in ThisBuild ++= Seq(
  "Maven Central Google Mirror EU" at "https://maven-central-eu.storage-download.googleapis.com/repos/central/data/",
  s3Resolver
)
version in ThisBuild ~= (_.replace('+', '-'))
dynver in ThisBuild ~= (_.replace('+', '-'))
cancelable in ThisBuild := true
publish := { }

lazy val `Storage-partitioner-api` = (project in file("src/api"))
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-api", s3Resolver))
  .settings(libraryDependencies += druid4sUtils)

lazy val `Storage-partitioner-s3` = (project in file("src/core/s3"))
  .settings(libraryDependencies ++= Seq(alpakkaS3, s3mock, awsS3 % "test"))
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-s3", s3Resolver))
  .dependsOn(`Storage-partitioner-api` % "compile->compile;test->test")

lazy val `Storage-partitioner-cql` = (project in file("src/core/cql"))
  .settings(libraryDependencies ++= cassandraDeps :+ alpakkaCassandra)
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-cql", s3Resolver))
  .dependsOn(`Storage-partitioner-api` % "compile->compile;test->test")

lazy val `Storage-partitioner-druid` = (project in file("src/core/druid"))
  .dependsOn(`Storage-partitioner-api` % "compile->compile;test->test")
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-druid", s3Resolver))
  .settings(libraryDependencies += druid4sClient)

lazy val `Storage-partitioner-gcs` = (project in file("src/core/gcs"))
  .settings(libraryDependencies ++= Seq(alpakkaGCS))
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-gcs", s3Resolver))
  .dependsOn(
    `Storage-partitioner-s3` % "compile->compile;test->test",
    `Storage-partitioner-api` % "compile->compile;test->test"
  )

lazy val `Storage-partitioner-all` = (project in file("src/all"))
  .settings(libraryDependencies += sprayJson)
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner", s3Resolver))
  .dependsOn(
    `Storage-partitioner-api` % "compile->compile;test->test",
    `Storage-partitioner-s3` % "compile->compile;test->test",
    `Storage-partitioner-druid` % "compile->compile;test->test",
    `Storage-partitioner-cql` % "compile->compile;test->test",
    `Storage-partitioner-gcs` % "compile->compile;test->test"
  )
