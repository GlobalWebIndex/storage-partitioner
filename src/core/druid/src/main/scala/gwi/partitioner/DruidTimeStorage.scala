package gwi.partitioner

import gwi.druid.client.DruidClient
import org.joda.time.{DateTime, DateTimeZone, Interval}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions

case class DruidSource(dataSource: String, coordinator: String, overlord: String, broker: String, access: String, properties: Map[String,String]) extends StorageSource

case class DruidTimeStorage(id: String, source: DruidSource, partitioner: IdentityTimePartitioner) extends TimeStorage[DruidSource, IdentityTimePartitioner, TimeClient]

object DruidTimeStorage {
  implicit class DruidTimeStoragePimp(underlying: DruidTimeStorage) {
    def client(implicit driver: DruidClient) = new TimeClient {
      type OUT = IdentityPointer
      private val DruidTimeStorage(_, source, partitioner) = underlying

      def delete(partition: TimePartition): Unit = {} //deleting segments is not real-time, ie. delete & create of the same partition would not have deterministic outcome

      def markWithSuccess(partition: TimePartition): Unit = {} // done by druid

      def list: Future[Seq[TimePartition]] =
        list(new Interval(new DateTime(2015, 1, 1, 0, 0, 0, DateTimeZone.UTC), partitioner.granularity.truncate(new DateTime(DateTimeZone.UTC))))

      def list(range: Interval): Future[Seq[TimePartition]] = {
        Future {
          driver.forQueryingCoordinator(source.coordinator)(10.seconds, 1.minute)
            .listDataSourceIntervals(source.dataSource).get
            .getOrElse(Seq.empty)
            .map(partitioner.buildPartition)
            .filter(p => range.contains(p.value))
            .sortWith { case (x, y) => x.value.getStart.compareTo(y.value.getStart) < 1 }
        }(ExeC.global)
      }

      def lookup(p: TimePartition): IdentityPointer = underlying.partitioner.construct(p, source)
    }
  }
}