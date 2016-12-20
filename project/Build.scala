import java.util.TimeZone

import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoKeys._
import sbtbuildinfo.{BuildInfoOption, _}

object Build extends sbt.Build {

  lazy val akkaVersion = "2.4.11"

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

  lazy val libraryDeps = Seq(
    "joda-time" % "joda-time" % "2.9.4",
    "org.joda" % "joda-convert" % "1.8",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  )

  val publishSettings = Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    publishArtifact in (Test, packageBin) := true,
    pomIncludeRepository := { _ => false },
    publishTo := Some("S3 Snapshots" at "s3://maven.globalwebindex.net.s3-website-eu-west-1.amazonaws.com/snapshots"),
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

  lazy val sharedSettings = Seq(
    organization := "net.globalwebindex",
    version := "0.06-SNAPSHOT",
    scalaVersion := "2.11.8",
    offline := true,
    scalacOptions ++= Seq(
      "-unchecked", "-deprecation", "-feature", "-Xfatal-warnings",
      "-Xlint", "-Xfuture",
      "-Yinline-warnings", "-Ywarn-adapted-args", "-Ywarn-inaccessible",
      "-Ywarn-nullary-override", "-Ywarn-nullary-unit", "-Yno-adapted-args"
    ),
    libraryDependencies ++= libraryDeps,
    autoCompilerPlugins := true,
    cancelable in Global := true,
    resolvers ++= Seq(
      "S3 Snapshots" at "s3://maven.globalwebindex.net.s3-website-eu-west-1.amazonaws.com/snapshots",
      Resolver.sonatypeRepo("snapshots"),
      Resolver.typesafeRepo("releases"),
      Resolver.mavenLocal
    )
  ) ++ testSettings ++ publishSettings

  lazy val api = (project in file("src/api"))
    .enablePlugins(BuildInfoPlugin)
    .settings(name := "storage-partitioner-api")
    .settings(sharedSettings)
    .settings(buildInfoKeys := Seq[BuildInfoKey](name, version))
    .settings(buildInfoOptions ++= Seq(BuildInfoOption.ToJson))
    .settings(buildInfoPackage := "gwi.partitioner")

  lazy val s3 = (project in file("src/core/s3"))
    .settings(name := "s3-storage-partitioner")
    .settings(sharedSettings)
    .settings(libraryDependencies ++= Seq("com.amazonaws" % "aws-java-sdk-s3" % "1.11.68"))
    .dependsOn(api % "compile->compile;test->test")

  lazy val druid = (project in file("src/core/druid"))
    .settings(name := "druid-storage-partitioner")
    .settings(sharedSettings)
    .settings(libraryDependencies ++= Seq("net.globalwebindex" %% "scala-druid-client" % "0.1.1-SNAPSHOT"))
    .dependsOn(api % "compile->compile;test->test")

  lazy val all = (project in file("src/all"))
    .settings(name := "storage-partitioner")
    .settings(sharedSettings)
    .settings(libraryDependencies ++= Seq(
        "io.spray" %% "spray-json" % "1.3.2"
      )
    ).dependsOn(s3 % "compile->compile;test->test", druid % "compile->compile;test->test")

}