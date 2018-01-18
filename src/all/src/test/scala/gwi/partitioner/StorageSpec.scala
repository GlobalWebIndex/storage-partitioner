package gwi.partitioner

import com.amazonaws.regions.DefaultAwsRegionProviderChain
import com.datastax.driver.core.Session
import gwi.druid.utils.Granularity
import gwi.druid.client._
import org.scalatest._

class StorageSpec extends FreeSpec with StorageCodec with AkkaSupport with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  val s3S = S3TimeStorage("foo", S3Source("bar", "baz/", "rw", Set("version-foo"), Map.empty), S3TimePartitioner.plain(Granularity.HOUR))
  val druidS = DruidTimeStorage("foo", DruidSource("bar", "baz", "fuz", "huz", "rw", Set("version-foo"), Map.empty), PlainTimePartitioner(Granularity.HOUR))
  val memS = MemoryTimeStorage("foo", new MemorySource("rw", Set("version-foo"), Seq.empty, Map.empty), PlainTimePartitioner(Granularity.HOUR))

  implicit val s3 = S3Driver("x","y", new DefaultAwsRegionProviderChain)
  implicit val druid = DruidClient
  implicit val session: Session = null

  "storages should" - {

    "be configurable" in {
      Config.load[S3TimeStorage]("foo")
      val druidStorage = Config.load[DruidTimeStorage]("bar")
      assert(!druidStorage.toString.contains('$'))
      Config.load(List("foo", "bar"))
    }

    "be serializable" in {
      import spray.json._
      s3S.toJson
      druidS.toJson
      val stores = List[TimeStorage.*](s3S, druidS)
      stores.map(storage => storage.getClient)
    }

    "have client available" in {
      s3S.client
      druidS.client
      memS.client
    }
  }

}
