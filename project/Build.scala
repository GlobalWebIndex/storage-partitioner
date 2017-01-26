import java.util.TimeZone

import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoKeys._
import sbtbuildinfo.{BuildInfoOption, _}

object Build extends sbt.Build {

  lazy val appVersion  = "0.09-SNAPSHOT"

  lazy val druid4s        = "net.globalwebindex"      %% "druid4s"                  % "0.1.2-SNAPSHOT"
  lazy val awsDeps        = "com.amazonaws"           %  "aws-java-sdk-s3"          % "1.11.68"
  lazy val sprayDeps      = "io.spray"                %% "spray-json"               % "1.3.2"
  lazy val jodaTime       = Seq(
                            "joda-time"               %  "joda-time"                % "2.9.4",
                            "org.joda"                %  "joda-convert"             % "1.8"
                          )
  lazy val testingDeps    = Seq(
                            "org.scalatest"           %% "scalatest"                % "3.0.0"               % "test",
                            "com.storm-enroute"       %% "scalameter"               % "0.8.2"               % "test"
                          )

  lazy val testSettings = Seq(
    fork in Test := false,
    testOptions in Test ++= Seq(Tests.Argument("-oDFI"), Tests.Setup( () => TimeZone.setDefault(TimeZone.getTimeZone("UTC")) )),
    parallelExecution in Test := false,
    parallelExecution in ThisBuild := false,
    parallelExecution in IntegrationTest := false,
    testForkedParallel in ThisBuild := true,
    testForkedParallel in IntegrationTest := true,
    testForkedParallel in Test := true,
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
    initialCommands in(Test, console) := """ammonite.repl.Main().run()"""
  )

  lazy val publishSettings = Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    publishArtifact in (Test, packageBin) := true,
    pomIncludeRepository := { _ => false },
    publishTo := Some("S3 Snapshots" at "s3://public.maven.globalwebindex.net.s3-website-eu-west-1.amazonaws.com/snapshots"),
    pomExtra :=
      <url>https://github.com/GlobalWebIndex/storage-partitioner</url>
        <licenses>
          <license>
            <name>The MIT License (MIT)</name>
            <url>http://opensource.org/licenses/MIT</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:GlobalWebIndex/storage-partitioner.git</url>
          <connection>scm:git:git@github.com:GlobalWebIndex/storage-partitioner.git</connection>
        </scm>
        <developers>
          <developer>
            <id>l15k4</id>
            <name>Jakub Liska</name>
            <email>jakub@globalwebindex.net</email>
          </developer>
        </developers>
  )

  override lazy val settings = super.settings ++ Seq(
    organization := "net.globalwebindex",
    version := appVersion,
    scalaVersion := "2.11.8",
    offline := true,
    scalacOptions ++= Seq(
      "-unchecked", "-deprecation", "-feature", "-Xfatal-warnings",
      "-Xlint", "-Xfuture",
      "-Yinline-warnings", "-Ywarn-adapted-args", "-Ywarn-inaccessible",
      "-Ywarn-nullary-override", "-Ywarn-nullary-unit", "-Yno-adapted-args"
    ),
    libraryDependencies ++= jodaTime ++ testingDeps,
    autoCompilerPlugins := true,
    cancelable in Global := true,
    resolvers ++= Seq(
      "S3 Snapshots" at "s3://public.maven.globalwebindex.net.s3-website-eu-west-1.amazonaws.com/snapshots",
      Resolver.sonatypeRepo("snapshots"),
      Resolver.typesafeRepo("releases"),
      Resolver.mavenLocal
    )
  ) ++ testSettings ++ publishSettings

  lazy val `storage-partitioner` = (project in file("."))
    .aggregate(api, s3, druid, all)

  lazy val api = (project in file("src/api"))
    .enablePlugins(BuildInfoPlugin)
    .settings(name := "storage-partitioner-api")
    .settings(buildInfoKeys := Seq[BuildInfoKey](name, version))
    .settings(buildInfoOptions ++= Seq(BuildInfoOption.ToJson))
    .settings(buildInfoPackage := "gwi.partitioner")

  lazy val s3 = (project in file("src/core/s3"))
    .settings(name := "s3-storage-partitioner")
    .settings(libraryDependencies += awsDeps)
    .dependsOn(api % "compile->compile;test->test")

  lazy val druid = (project in file("src/core/druid"))
    .settings(name := "druid-storage-partitioner")
    .settings(libraryDependencies += druid4s)
    .dependsOn(api % "compile->compile;test->test")

  lazy val all = (project in file("src/all"))
    .settings(name := "storage-partitioner")
    .settings(libraryDependencies += sprayDeps)
    .dependsOn(s3 % "compile->compile;test->test", druid % "compile->compile;test->test")

}