package gwi.partitioner

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.s3.scaladsl.S3Client
import akka.stream.alpakka.s3.{MemoryBufferType, S3Settings}
import akka.util.Timeout
import com.amazonaws.auth.{AWSStaticCredentialsProvider, AnonymousAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.DefaultAwsRegionProviderChain
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}
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

trait AkkaSupport extends Suite with BeforeAndAfterAll {
  protected[this] implicit val timeout = Timeout(10.seconds)
  protected[this] implicit lazy val system = ActorSystem("AkkaSuiteSystem")
  protected[this] implicit lazy val materializer = ActorMaterializer()

  override def afterAll(): Unit = try super.afterAll() finally {
    Await.ready(Future(system.terminate())(ExecutionContext.global), Duration.Inf)
  }
}

sealed trait S3ClientProvider extends AkkaSupport {
  protected[this] val randomPort: Int = Random.nextInt(1000) + 4000
  protected[this] implicit lazy val s3Client =
    new akka.stream.alpakka.s3.scaladsl.S3Client(
      new S3Settings(
        MemoryBufferType,
        proxy = None,
        new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()),
        new DefaultAwsRegionProviderChain,
        pathStyleAccess = true,
        endpointUrl = Some(s"http://localhost:$randomPort"),
//        listBucketApiVersion = ListBucketVersion2.getInstance
      ),
    )

  protected[this] val legacyClient = // still needed for creating buckets
    AmazonS3ClientBuilder
      .standard()
      .withPathStyleAccessEnabled(true)
      .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s"http://localhost:$randomPort", "eu-west-1"))
      .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
      .build()

  protected[this] def startS3(prepare: => Unit): Unit
  protected[this] def stopS3(cleanup: => Unit): Unit
}

trait FakeS3 extends S3ClientProvider with DockerSupport {
  protected[this] val randomName: String = s"fake-s3-$randomPort"

  protected[this] def startS3(prepare: => Unit): Unit =
    startContainer("gwiq/fake-s3", randomName, Seq(PPorts.from(randomPort, randomPort)), Some(s"-r /fakes3_root -p $randomPort"))(prepare)

  protected[this] def stopS3(cleanup: => Unit): Unit =
    stopContainer(randomName)(cleanup)
}

trait S3DockerMock extends S3ClientProvider with DockerSupport {
  protected[this] val randomName: String = s"s3-mock-$randomPort"

  protected[this] def startS3(prepare: => Unit): Unit =
    startContainer("findify/s3mock", randomName, Seq(PPorts.from(randomPort, 8001)), None)(prepare)

  protected[this] def stopS3(cleanup: => Unit): Unit =
    stopContainer(randomName)(cleanup)
}

trait S3Mock extends S3ClientProvider {
  private val api = io.findify.s3mock.S3Mock(port = randomPort, dir = "/tmp/s3")

  protected[this] def startS3(prepare: => Unit): Unit = {
    api.start
    prepare
  }

  protected[this] def stopS3(cleanup: => Unit): Unit = {
    try cleanup finally api.stop
  }
}