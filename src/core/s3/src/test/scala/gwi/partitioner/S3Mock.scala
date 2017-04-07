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
  implicit val timeout = Timeout(10.seconds)
  implicit lazy val system = ActorSystem("AkkaSuiteSystem")
  implicit lazy val materializer = ActorMaterializer()

  override def afterAll(): Unit = try super.afterAll() finally {
    Await.ready(Future(system.terminate())(ExecutionContext.global), Duration.Inf)
  }
}

trait S3Mock extends AkkaSupport {

  private def docker(cmd: String) = Array("/bin/sh", "-c", s"docker $cmd")
  private val randomPort = Random.nextInt(1000) + 4000
  private val randomName = s"fake-s3-$randomPort"

  protected def startS3Container(prepare: => Unit): Unit = {
    require(Process(docker(s"run --name $randomName -p $randomPort:$randomPort -d gwiq/fake-s3 -r /fakes3_root -p $randomPort")).run().exitValue == 0)
    Thread.sleep(500)
    prepare
  }

  protected def stopS3Container(cleanup: => Unit): Unit = {
    Try(cleanup)
    Process(docker(s"stop $randomName")).run()
    Process(docker(s"rm -fv $randomName")).run()
  }

  implicit lazy val s3Driver = {
    val s3 = S3Driver("foo", "bar", "eu-west-1")
    s3.setEndpoint(s"http://localhost:$randomPort")
    s3.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).disableChunkedEncoding().build())
    s3
  }

}