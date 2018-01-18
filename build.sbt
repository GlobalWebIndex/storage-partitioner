
version in ThisBuild := "0.3.1"
crossScalaVersions in ThisBuild := Seq("2.12.4", "2.11.8")
organization in ThisBuild := "net.globalwebindex"
libraryDependencies in ThisBuild ++= Seq(monix, akkaActor, akkaStream, scalatest, scalameter, loggingImplLog4j % "test") ++ jodaTime

lazy val druid4sVersion = "0.2.1"

lazy val `Storage-partitioner` = (project in file("."))
  .settings(aggregate in update := false)
  .settings(publish := { })
  .aggregate(`Storage-partitioner-api`, `Storage-partitioner-s3`, `Storage-partitioner-druid`, `Storage-partitioner-all`)

lazy val `Storage-partitioner-api` = (project in file("src/api"))
  .enablePlugins(CommonPlugin)
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-api", s3Resolver))
  .dependsOn(
    ProjectRef(uri(s"https://github.com/GlobalWebIndex/druid4s.git#v$druid4sVersion"), "Druid4s-utils") % "compile->compile;test->test"
  )

lazy val `Storage-partitioner-s3` = (project in file("src/core/s3"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies ++= Seq(awsS3, alpakkaS3, s3mock))
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-s3", s3Resolver))
  .dependsOn(
    `Storage-partitioner-api` % "compile->compile;test->test"
  )

lazy val `Storage-partitioner-cql` = (project in file("src/core/cql"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies ++= Seq(cassandraDriver, alpakkaCassandra))
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-cql", s3Resolver))
  .dependsOn(
    `Storage-partitioner-api` % "compile->compile;test->test"
  )

lazy val `Storage-partitioner-druid` = (project in file("src/core/druid"))
  .enablePlugins(CommonPlugin)
  .dependsOn(`Storage-partitioner-api` % "compile->compile;test->test")
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-druid", s3Resolver))
  .dependsOn(
    ProjectRef(uri(s"https://github.com/GlobalWebIndex/druid4s.git#v$druid4sVersion"), "Druid4s-client") % "compile->compile;test->test"
  )

lazy val `Storage-partitioner-all` = (project in file("src/all"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies += sprayJson)
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner", s3Resolver))
  .dependsOn(
    `Storage-partitioner-api` % "compile->compile;test->test",
    `Storage-partitioner-s3` % "compile->compile;test->test",
    `Storage-partitioner-druid` % "compile->compile;test->test",
    `Storage-partitioner-cql` % "compile->compile;test->test"
  )
