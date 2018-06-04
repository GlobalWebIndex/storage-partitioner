package gwi.partitioner

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.Await
import scala.concurrent.duration._

trait AkkaSupport extends Suite with BeforeAndAfterAll {
  protected[this] implicit val timeout = Timeout(10.seconds)
  protected[this] implicit lazy val system = ActorSystem("AkkaSuiteSystem")
  protected[this] implicit lazy val materializer = ActorMaterializer()
  private[this] val http = Http(system)

  override def afterAll(): Unit = try super.afterAll() finally {
    Await.result(http.shutdownAllConnectionPools(), 10.seconds)
    Await.result(system.terminate(), 10.seconds)
  }
}
