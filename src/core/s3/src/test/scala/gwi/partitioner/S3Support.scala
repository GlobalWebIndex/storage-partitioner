package gwi.partitioner

import akka.stream.alpakka.s3.{MemoryBufferType, S3Settings}
import com.amazonaws.auth.{AWSStaticCredentialsProvider, AnonymousAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.DefaultAwsRegionProviderChain
import com.amazonaws.services.s3.AmazonS3ClientBuilder

import scala.sys.process.Process
import scala.util.{Random, Try}

trait DockerSupport {
  case class PPorts(host: String, guest: String)
  object PPorts {
    def from(host: Int, guest: Int) = new PPorts(host.toString, guest.toString)
  }
  private[this] def docker(cmd: String) = Array("/bin/sh", "-c", s"docker $cmd")

  private[this] def portsToString(ports: Seq[PPorts]) =
    if (ports.isEmpty) "" else ports.map { case PPorts(host, guest) => s"$host:$guest" }.mkString("-p ", " -p ", "")

  protected[this] def startContainer(image: String, name: String, ports: Seq[PPorts], args: Option[String] = None)(prepare: => Unit): Unit = {
    require(Process(docker(s"run --name $name ${portsToString(ports)} -d $image ${args.getOrElse("")}")).run().exitValue == 0)
    Thread.sleep(1000)
    prepare
  }

  protected[this] def stopContainer(name: String)(cleanup: => Unit): Unit = {
    Try(cleanup)
    Process(docker(s"stop $name")).run()
    Process(docker(s"rm -fv $name")).run()
    Thread.sleep(500)
  }
}

trait S3ClientProvider extends AkkaSupport {
  protected[this] val dockerPort: Int
  protected[this] val dockerHost: String
  protected[this] implicit lazy val s3Client =
    AlpakkaS3Client(
      new S3Settings(
        MemoryBufferType,
        proxy = None,
        new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()),
        new DefaultAwsRegionProviderChain,
        pathStyleAccess = true,
        endpointUrl = Some(s"http://$dockerHost:$dockerPort")
      )
    )

  protected[this] implicit lazy val legacyClient = // still needed for creating buckets
    AmazonS3ClientBuilder
      .standard()
      .withPathStyleAccessEnabled(true)
      .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s"http://$dockerHost:$dockerPort", "eu-west-1"))
      .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
      .build()

}

trait FakeS3 extends S3ClientProvider with DockerSupport {
  protected[this] val dockerPort: Int = Random.nextInt(1000) + 4000
  protected[this] val dockerHost: String = "localhost"
  protected[this] val randomName: String = s"fake-s3-$dockerPort"

  protected[this] def startS3(prepare: => Unit): Unit =
    startContainer("gwiq/fake-s3", randomName, Seq(PPorts.from(dockerPort, dockerPort)), Some(s"-r /fakes3_root -p $dockerPort"))(prepare)

  protected[this] def stopS3(cleanup: => Unit): Unit =
    stopContainer(randomName)(cleanup)
}

trait S3DockerMock extends S3ClientProvider with DockerSupport {
  protected[this] val dockerPort: Int = Random.nextInt(1000) + 4000
  protected[this] val dockerHost: String = "localhost"
  protected[this] val randomName: String = s"s3-mock-$dockerPort"

  protected[this] def startS3(prepare: => Unit): Unit =
    startContainer("findify/s3mock", randomName, Seq(PPorts.from(dockerPort, 8001)), None)(prepare)

  protected[this] def stopS3(cleanup: => Unit): Unit =
    stopContainer(randomName)(cleanup)
}

trait S3Mock extends S3ClientProvider {
  protected[this] val dockerPort: Int = Random.nextInt(1000) + 4000
  protected[this] val dockerHost: String = "localhost"
  private val api = io.findify.s3mock.S3Mock(port = dockerPort, dir = "/tmp/s3")

  protected[this] def startS3(prepare: => Unit): Unit = {
    api.start
    prepare
  }

  protected[this] def stopS3(cleanup: => Unit): Unit = {
    try cleanup finally api.stop
  }
}