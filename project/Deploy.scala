import sbt.Keys._
import sbt._

object Deploy {
  def publishSettings(organization: String, projectName: String, resolverOpt: sbt.Resolver) = Seq(
    publishTo := Some(resolverOpt),
    publishMavenStyle := true,
    publishArtifact := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra :=
      <url>https://github.com/{organization}/{projectName}</url>
        <licenses>
          <license>
            <name>The MIT License (MIT)</name>
            <url>http://opensource.org/licenses/MIT</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:{organization}/{projectName}.git</url>
          <connection>scm:git:git@github.com:{organization}/{projectName}.git</connection>
        </scm>
        <developers>
          <developer>
            <id>l15k4</id>
            <name>Jakub Liska</name>
            <email>liska.jakub@gmail.com</email>
          </developer>
        </developers>
  )
}