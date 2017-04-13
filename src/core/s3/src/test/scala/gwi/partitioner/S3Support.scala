package gwi.partitioner

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.amazonaws.services.s3.S3ClientOptions
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.sys.process.Process
import scala.util.{Random, Try}

trait AkkaSupport extends Suite with BeforeAndAfterAll {
  protected[this] implicit val timeout = Timeout(10.seconds)
  protected[this] implicit lazy val system = ActorSystem("AkkaSuiteSystem")
  protected[this] implicit lazy val materializer = ActorMaterializer()

  override def afterAll(): Unit = try super.afterAll() finally {
    Await.ready(Future(system.terminate())(ExecutionContext.global), Duration.Inf)
  }
}

sealed trait S3DriverProvider extends AkkaSupport {
  protected[this] val randomPort: Int = Random.nextInt(1000) + 4000
  implicit lazy val s3Driver: S3Driver = {
    val s3 = S3Driver("foo", "bar", "eu-west-1")
    s3.setEndpoint(s"http://localhost:$randomPort")
    s3.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).disableChunkedEncoding().build())
    s3
  }
}

trait FakeS3 extends S3DriverProvider {
  private def docker(cmd: String) = Array("/bin/sh", "-c", s"docker $cmd")
  private val randomName = s"fake-s3-$randomPort"

  protected[this] def startS3Container(prepare: => Unit): Unit = {
    require(Process(docker(s"run --name $randomName -p $randomPort:$randomPort -d gwiq/fake-s3 -r /fakes3_root -p $randomPort")).run().exitValue == 0)
    Thread.sleep(500)
    prepare
  }

  protected[this] def stopS3Container(cleanup: => Unit): Unit = {
    Try(cleanup)
    Process(docker(s"stop $randomName")).run()
    Process(docker(s"rm -fv $randomName")).run()
  }
}

trait S3Mock extends S3DriverProvider {
  private val api = io.findify.s3mock.S3Mock(port = randomPort, dir = "/tmp/s3")

  protected[this] def startS3Container(prepare: => Unit): Unit = {
    api.start
    prepare
  }

  protected[this] def stopS3Container(cleanup: => Unit): Unit = {
    try cleanup finally api.stop
  }
}