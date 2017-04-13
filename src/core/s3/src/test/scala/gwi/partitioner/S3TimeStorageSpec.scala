package gwi.partitioner

import org.joda.time.{DateTime, DateTimeZone, Interval}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FreeSpec, Matchers}

class S3TimeStorageSpec extends FreeSpec with S3Mock with ScalaFutures with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  implicit val futurePatience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  private[this] val bucket = "foo"
  private[this] val path = "bar/"
  private[this] val source = S3Source(bucket, path, "rw", Map.empty)
  private[this] val plainStorage = S3TimeStorage("foo", source, S3TimePartitioner.plain(Granularity.HOUR))
  private[this] val qualifiedStorage = S3TimeStorage("foo", source, S3TimePartitioner.qualified(Granularity.HOUR))
  private[this] val partitions = plainStorage.liftMany(new Interval(new DateTime(2016, 1, 1, 22, 0, 0, DateTimeZone.UTC), new DateTime(2016, 1, 2, 5, 0, 0, DateTimeZone.UTC))).toVector.sortBy(_.value.toString)

  private[this] def createStorage(storage: S3TimeStorage, partitions: Iterable[TimePartition]): Unit = {
    val client = storage.client
    partitions.foreach { partition =>
      client.indexData(partition, "test.json", s"""{"timestamp":"${partition.value.getStart.toString}", "foo":"bar"}""")
      client.markWithSuccess(partition)
    }
  }

  override def beforeAll(): Unit = try super.beforeAll() finally {
    startS3Container {
      s3Driver.createBucket(bucket)
      createStorage(plainStorage, partitions)
    }
  }

  override def afterAll(): Unit = try super.afterAll() finally {
    stopS3Container(())
  }

  "S3 time storage should" - {
    "lift path" in {
      assertResult(S3TimePartition(bucket, path, "2011/02/03/04/", new Interval("2011-02-03T04:00:00.000/2011-02-03T05:00:00.000")))(plainStorage.lift(plainStorage.partitioner.pathToInterval("bla/2011/02/03/04/")))
      assertResult(S3TimePartition(bucket, path, "y=2011/m=02/d=03/H=04/", new Interval("2011-02-03T04:00:00.000/2011-02-03T05:00:00.000")))(qualifiedStorage.lift(qualifiedStorage.partitioner.pathToInterval("bla/y=2011/m=02/d=03/H=04/")))
    }
    "list partitions" in {
      whenReady(plainStorage.client.list) { actualPartitions =>
        assertResult(partitions)(actualPartitions.sortBy(_.value.toString))
      }
    }
    "delete partitions" in {
      plainStorage.client.delete(partitions.head)
      whenReady(plainStorage.client.list) { actualPartitions =>
        assertResult(partitions.tail)(actualPartitions.sortBy(_.value.toString))
      }
    }
  }

}
