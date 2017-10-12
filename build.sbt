
version in ThisBuild := "0.1.2"
crossScalaVersions in ThisBuild := Seq("2.12.3", "2.11.8")
organization in ThisBuild := "net.globalwebindex"
libraryDependencies in ThisBuild ++= Seq(monix, akkaActor, akkaStream, scalatest, scalameter, loggingImplLog4j % "test") ++ jodaTime

lazy val druid4sVersion = "0.0.4"

lazy val `storage-partitioner` = (project in file("."))
  .settings(aggregate in update := false)
  .settings(publish := { })
  .aggregate(`storage-partitioner-api`, `storage-partitioner-s3`, `storage-partitioner-druid`, `storage-partitioner-all`)

lazy val `storage-partitioner-api` = (project in file("src/api"))
  .enablePlugins(CommonPlugin)
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-api", s3Resolver))

lazy val `storage-partitioner-s3` = (project in file("src/core/s3"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies ++= Seq(awsS3, alpakkaS3, s3mock))
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-s3", s3Resolver))
  .dependsOn(
    `storage-partitioner-api` % "compile->compile;test->test"
  )

lazy val `storage-partitioner-cql` = (project in file("src/core/cql"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies ++= Seq(cassandraDriver, alpakkaCassandra))
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-cql", s3Resolver))
  .dependsOn(
    `storage-partitioner-api` % "compile->compile;test->test"
  )

lazy val `storage-partitioner-druid` = (project in file("src/core/druid"))
  .enablePlugins(CommonPlugin)
  .dependsOn(`storage-partitioner-api` % "compile->compile;test->test")
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-druid", s3Resolver))
  .dependsOn(
    ProjectRef(uri(s"https://github.com/GlobalWebIndex/druid4s.git#v$druid4sVersion"), "druid4s-client") % "compile->compile;test->test"
  )

lazy val `storage-partitioner-all` = (project in file("src/all"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies += sprayJson)
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner", s3Resolver))
  .dependsOn(
    `storage-partitioner-api` % "compile->compile;test->test",
    `storage-partitioner-s3` % "compile->compile;test->test",
    `storage-partitioner-druid` % "compile->compile;test->test",
    `storage-partitioner-cql` % "compile->compile;test->test"
  )
