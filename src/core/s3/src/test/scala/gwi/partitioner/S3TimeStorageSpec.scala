package gwi.partitioner

import org.joda.time.{DateTime, DateTimeZone, Interval}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FreeSpec, Matchers}

class S3TimeStorageSpec extends FreeSpec with S3Mock with ScalaFutures with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  implicit val futurePatience = PatienceConfig(timeout = Span(3, Seconds), interval = Span(100, Millis))

  def createStorage(storage: S3TimeStorage, partitions: Iterable[TimePartition]): Unit = {
    val client = storage.client
    partitions.foreach { partition =>
      client.indexData(partition, "test.json", s"""{"timestamp":"${partition.value.getStart.toString}", "foo":"bar"}""")
      client.markWithSuccess(partition)
    }
  }

  val storage = S3TimeStorage("foo", S3Source("foo", "bar/", "rw", Map.empty), S3TimePartitioner.plain(Granularity.HOUR))
  val partitions = storage.partitioner.buildPartitions(new Interval(new DateTime(2016, 1, 1, 22, 0, 0, DateTimeZone.UTC), new DateTime(2016, 1, 2, 5, 0, 0, DateTimeZone.UTC))).toVector

  override def beforeAll(): Unit = try super.beforeAll() finally {
    startS3Container(createStorage(storage, partitions))
  }

  override def afterAll(): Unit = try super.afterAll() finally {
    stopS3Container(())
  }

  "S3 time storage should" - {
    "list partitions" in {
      whenReady(storage.client.list) { actualPartitions =>
        assertResult(partitions)(actualPartitions)
      }
    }
    "delete partitions" in {
      storage.client.delete(partitions.head)
      whenReady(storage.client.list) { actualPartitions =>
        assertResult(partitions.tail)(actualPartitions)
      }
    }
  }

}
