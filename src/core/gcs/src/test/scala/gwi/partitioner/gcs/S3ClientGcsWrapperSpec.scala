package gwi.partitioner.gcs

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.scalatest.FreeSpec

import scala.concurrent.duration._

class S3ClientGcsWrapperSpec extends FreeSpec {

  private implicit val timeout = Timeout(10.seconds)
  private implicit lazy val system = ActorSystem("AkkaSuiteSystem")
  private implicit lazy val materializer = ActorMaterializer()



}
