version in ThisBuild := "0.4.7"

crossScalaVersions in ThisBuild := Seq("2.12.4", "2.11.8")
organization in ThisBuild := "net.globalwebindex"
libraryDependencies in ThisBuild ++= Seq(monix, akkaActor, akkaStream, scalatest, scalameter, loggingImplLogback % "test") ++ jodaTime ++ loggingApi

lazy val druid4sVersion = "0.3.0"

lazy val alpakkaGCS = "com.lightbend.akka" %% "akka-stream-alpakka-google-cloud-storage" % "0.16-gcs"
lazy val alpakkaS3 = "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "0.16-gcs"

lazy val `Storage-partitioner` = (project in file("."))
  .settings(aggregate in update := false)
  .settings(publish := { })
  .aggregate(`Storage-partitioner-api`, `Storage-partitioner-s3`, `Storage-partitioner-druid`, `Storage-partitioner-all`, `Storage-partitioner-gcs`)

lazy val `Storage-partitioner-api` = (project in file("src/api"))
  .enablePlugins(CommonPlugin)
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-api", s3Resolver))
  .dependsOn(
    ProjectRef(uri(s"https://github.com/GlobalWebIndex/druid4s.git#v$druid4sVersion"), "Druid4s-utils") % "compile->compile;test->test"
  )

lazy val `Storage-partitioner-s3` = (project in file("src/core/s3"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies ++= Seq(alpakkaS3, s3mock, awsS3 % "test"))
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

lazy val `Storage-partitioner-gcs` = (project in file("src/core/gcs"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies ++= Seq(alpakkaGCS))
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-gcs", s3Resolver))
  .dependsOn(
    `Storage-partitioner-s3` % "compile->compile;test->test",
    `Storage-partitioner-api` % "compile->compile;test->test"
  )


lazy val `Storage-partitioner-all` = (project in file("src/all"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies += sprayJson)
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner", s3Resolver))
  .dependsOn(
    `Storage-partitioner-api` % "compile->compile;test->test",
    `Storage-partitioner-s3` % "compile->compile;test->test",
    `Storage-partitioner-druid` % "compile->compile;test->test",
    `Storage-partitioner-cql` % "compile->compile;test->test",
    `Storage-partitioner-gcs` % "compile->compile;test->test"
  )
