package gwi.partitioner

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import gwi.druid.utils.Granularity
import org.joda.time.{DateTime, DateTimeZone, Interval}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class S3TimeStorageSpec extends FreeSpec with S3ClientProvider with ScalaFutures with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {
  implicit val futurePatience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))
  protected[this] val dockerPort: Int = 4567
  protected[this] val dockerHost: String = "fakes3"
  private[this] val bucket = "foo"
  private[this] val path = "bar/"
  private[this] val source = S3Source(bucket, path, "rw", Set("version-foo"), Map.empty)
  private[this] val plainStorage = S3TimeStorage("foo", source, S3TimePartitioner.plain(Granularity.HOUR))
  private[this] val qualifiedStorage = S3TimeStorage("foo", source, S3TimePartitioner.qualified(Granularity.HOUR))
  private[this] val interval = new Interval(new DateTime(2016, 1, 1, 22, 0, 0, DateTimeZone.UTC), new DateTime(2016, 1, 2, 5, 0, 0, DateTimeZone.UTC))
  private[this] val partitions = plainStorage.partitioner.buildMany(interval).toVector.sortBy(_.value.toString)

  private[this] def createStorage(storage: S3TimeStorage, partitions: Iterable[TimePartition]): Unit = {
    val client = storage.client
    partitions.foreach { partition =>
      val data = s"""{"timestamp":"${partition.value.getStart.toString}", "foo":"bar"}"""
      Await.ready(client.indexData(partition, "test.json", Source.single(ByteString(data)), data.length), 5.seconds)
      Await.ready(client.markWithSuccess(partition), 5.seconds)
    }
  }

  override def beforeAll(): Unit = try super.beforeAll() finally {
    legacyClient.createBucket(bucket)
    createStorage(plainStorage, partitions)
    Thread.sleep(500)
  }

  "S3 time storage should" - {
    "lift path" in {
      assertResult(S3TimePartition(bucket, path, "2011/02/03/04/", new Interval("2011-02-03T04:00:00.000/2011-02-03T05:00:00.000")))(plainStorage.lift(plainStorage.partitioner.pathToInterval("bla/2011/02/03/04/")))
      assertResult(S3TimePartition(bucket, path, "y=2011/m=02/d=03/H=04/", new Interval("2011-02-03T04:00:00.000/2011-02-03T05:00:00.000")))(qualifiedStorage.lift(qualifiedStorage.partitioner.pathToInterval("bla/y=2011/m=02/d=03/H=04/")))
    }
    "list partitions" in {
      whenReady(plainStorage.client.list(interval)) { actualPartitions =>
        assertResult(partitions)(actualPartitions.sortBy(_.value.toString))
        actualPartitions.map(plainStorage.lift).map(_.partitionFileKey(S3TimeStorage.SuccessFileName)).foreach { successFileKey =>
          assertResult("version-foo\n")(Await.result(s3Client.download(bucket, successFileKey).map(_.utf8String).runWith(Sink.head), 5.seconds))
        }
      }
    }
    "delete partitions" in {
      plainStorage.client.delete(partitions.head)
      Thread.sleep(1000)
      whenReady(plainStorage.client.list(interval)) { actualPartitions =>
        assertResult(partitions.tail)(actualPartitions.sortBy(_.value.toString))
      }
    }
  }

}
