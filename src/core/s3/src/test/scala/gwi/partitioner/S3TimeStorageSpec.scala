package gwi.partitioner

import gwi.partitioner.Granularity.HOUR
import org.joda.time.{DateTime, DateTimeZone, Interval}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FreeSpec, Matchers}

class S3TimeStorageSpec extends FreeSpec with S3Mock with ScalaFutures with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  implicit val futurePatience = PatienceConfig(timeout = Span(3, Seconds), interval = Span(100, Millis))

  private[this] val bucket = "foo"
  private[this] val path = "bar/"
  private[this] val source = S3Source(bucket, path, "rw", Map.empty)
  private[this] val plainStorage = S3TimeStorage("foo", source, S3TimePartitioner.plain(Granularity.HOUR))
  private[this] val qualifiedStorage = S3TimeStorage("foo", source, S3TimePartitioner.qualified(Granularity.HOUR))
  private[this] val partitions = plainStorage.partitioner.buildPartitions(new Interval(new DateTime(2016, 1, 1, 22, 0, 0, DateTimeZone.UTC), new DateTime(2016, 1, 2, 5, 0, 0, DateTimeZone.UTC))).toVector

  private[this] def createStorage(storage: S3TimeStorage, partitions: Iterable[TimePartition]): Unit = {
    val client = storage.client
    partitions.foreach { partition =>
      client.indexData(partition, "test.json", s"""{"timestamp":"${partition.value.getStart.toString}", "foo":"bar"}""")
      client.markWithSuccess(partition)
    }
  }

  override def beforeAll(): Unit = try super.beforeAll() finally {
    startS3Container(createStorage(plainStorage, partitions))
  }

  override def afterAll(): Unit = try super.afterAll() finally {
    stopS3Container(())
  }

  "S3 time storage should" - {
    "lookup path" in {
      val plainPartitioner = S3TimePartitioner.plain(HOUR)
      val qualifiedPartitioner = S3TimePartitioner.qualified(HOUR)
      assertResult(S3TimePath(bucket, path, "2011/02/03/04/"))(plainStorage.lift(plainPartitioner.buildPartition("2011-02-03T04:00:00.000/2011-02-03T05:00:00.000")))
      assertResult(S3TimePath(bucket, path, "y=2011/m=02/d=03/H=04/"))(qualifiedStorage.lift(qualifiedPartitioner.buildPartition("2011-02-03T04:00:00.000/2011-02-03T05:00:00.000")))
    }
    "list partitions" in {
      whenReady(plainStorage.client.list) { actualPartitions =>
        assertResult(partitions)(actualPartitions)
      }
    }
    "delete partitions" in {
      plainStorage.client.delete(partitions.head)
      whenReady(plainStorage.client.list) { actualPartitions =>
        assertResult(partitions.tail)(actualPartitions)
      }
    }
  }

}
