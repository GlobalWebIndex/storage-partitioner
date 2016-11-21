package gwi.partitioner

import gwi.druid.client.DruidClient
import org.scalatest._

class StorageSpec extends FreeSpec with StorageCodec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  val s3S = S3TimeStorage("foo", S3Source("bar", "baz/", "rw", Map.empty), S3TimePartitioner.plain(Granularity.HOUR))
  val druidS = DruidTimeStorage("foo", DruidSource("bar", "baz", "fuz", "huz", "rw", Map.empty), IdentityTimePartitioner(Granularity.HOUR))
  val memS = MemoryTimeStorage("foo", new MemorySource("rw", Seq.empty, Map.empty), IdentityTimePartitioner(Granularity.HOUR))

  implicit val s3 = S3Driver("x","y","eu-west-1")
  implicit val druid = DruidClient

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
      stores.map(storage => storage.client)
    }

    "have client available" in {
      s3S.client
      druidS.client
      memS.client
    }
  }

}
