import sbt._

object Dependencies {


  val druid4sVersion                    = "0.4.0"
  val akkaVersion                       = "2.5.18"
  val alpakkaVersion                    = "0.20"
  val jacksonVersion                    = "2.9.2"

  lazy val druid4sUtils                 = "net.globalwebindex"            %%    "druid4s-utils"                             % druid4sVersion
  lazy val druid4sClient                = "net.globalwebindex"            %%    "druid4s-client"                            % druid4sVersion

  /** AKKA */

  lazy val akkaActor                    = "com.typesafe.akka"             %%    "akka-actor"                                % akkaVersion
  lazy val akkaStream                   = "com.typesafe.akka"             %%    "akka-stream"                               % akkaVersion
  lazy val akkaSlf4j                    = "com.typesafe.akka"             %%    "akka-slf4j"                                % akkaVersion

  /** ALPAKKAA */

  lazy val alpakkaGCS                   = "net.globalwebindex"            %%    "akka-stream-alpakka-google-cloud-storage"  % "0.16-gcs.2" // unmerged branch, we should do something about it soon as we depend on an old branch
  lazy val alpakkaS3                    = "com.lightbend.akka"            %%    "akka-stream-alpakka-s3"                    % alpakkaVersion
  lazy val alpakkaCassandra             = "com.lightbend.akka"            %%    "akka-stream-alpakka-cassandra"             % alpakkaVersion

  /** TESTING */

  lazy val scalatest                    = "org.scalatest"                 %%    "scalatest"                                 % "3.0.5"                 % "test"
  lazy val s3mock                       = "io.findify"                    %%    "s3mock"                                    % "0.2.5"                 % "test"

  /** COMMON DEPS */

  lazy val awsS3                        = "com.amazonaws"                 %     "aws-java-sdk-s3"                           % "1.11.413"
  lazy val loggingImplLogback           = "ch.qos.logback"                %     "logback-classic"                           % "1.2.3"
  lazy val cassandraDriver              = "com.datastax.cassandra"        %     "cassandra-driver-core"                     % "3.6.0"
  lazy val sprayJson                    = "io.spray"                      %%    "spray-json"                                % "1.3.4"
  lazy val monix                        = "io.monix"                      %%    "monix"                                     % "2.3.2"
  lazy val scalameter                   = "com.storm-enroute"             %%    "scalameter"                                % "0.8.2"                 % "test"
  lazy val nettyHandler                 = "io.netty"                      %     "netty-handler"                             % "4.1.29.Final"
  lazy val cassandraDriverNettyEpoll    = "io.netty"                      %     "netty-transport-native-epoll"              % "4.1.29.Final" classifier "linux-x86_64"
  lazy val cassandraDeps                = Seq(cassandraDriver, cassandraDriverNettyEpoll, nettyHandler)

  lazy val jodaTime                     = Seq(
                                          "joda-time"                     %     "joda-time"                                 % "2.9.9",
                                          "org.joda"                      %     "joda-convert"                              % "1.9.2"
                                        )
  lazy val jackson                      = Seq(
                                          "com.fasterxml.jackson.module"  %%    "jackson-module-scala"                      % jacksonVersion,
                                          "com.fasterxml.jackson.core"    %     "jackson-core"                              % jacksonVersion,
                                          "com.fasterxml.jackson.core"    %     "jackson-annotations"                       % jacksonVersion
                                        )
  lazy val loggingApi                   = Seq(
                                          "org.slf4j"                     %     "slf4j-api"                                 % "1.7.25",
                                          "com.typesafe.scala-logging"    %%    "scala-logging"                             % "3.9.0"
                                        )
}
