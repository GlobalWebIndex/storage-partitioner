import gwi.sbt.CommonPlugin
import gwi.sbt.CommonPlugin.autoImport._

crossScalaVersions in ThisBuild := Seq("2.11.8", "2.12.1")
organization in ThisBuild := "net.globalwebindex"
libraryDependencies in ThisBuild ++= jodaTime ++ testingDeps

lazy val `storage-partitioner` = (project in file("."))
  .aggregate(api, s3, druid, all)

lazy val api = (project in file("src/api"))
  .enablePlugins(CommonPlugin, BuildInfoPlugin)
  .settings(name := "storage-partitioner-api")
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-api", s3Resolver))
  .settings(buildInfoKeys := Seq[BuildInfoKey](name, version))
  .settings(buildInfoOptions ++= Seq(BuildInfoOption.ToJson))
  .settings(buildInfoPackage := "gwi.partitioner")

lazy val s3 = (project in file("src/core/s3"))
  .enablePlugins(CommonPlugin)
  .settings(name := "s3-storage-partitioner")
  .settings(libraryDependencies += awsS3)
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-s3", s3Resolver))
  .dependsOn(api % "compile->compile;test->test")

lazy val druid = (project in file("src/core/druid"))
  .enablePlugins(CommonPlugin)
  .settings(name := "druid-storage-partitioner")
  .dependsOn(api % "compile->compile;test->test")
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner-druid", s3Resolver))
  .dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/druid4s.git#v0.0.1"), "client") % "compile->compile;test->test")

lazy val all = (project in file("src/all"))
  .enablePlugins(CommonPlugin)
  .settings(name := "storage-partitioner")
  .settings(libraryDependencies += sprayJson)
  .settings(publishSettings("GlobalWebIndex", "storage-partitioner", s3Resolver))
  .dependsOn(s3 % "compile->compile;test->test", druid % "compile->compile;test->test")
