package gwi.partitioner

import akka.Done
import gwi.druid.client.DruidClient
import org.joda.time.chrono.ISOChronology
import org.joda.time.{DateTime, DateTimeZone, Interval}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

case class DruidSource(dataSource: String, coordinator: String, overlord: String, broker: String, access: String, meta: Set[String], properties: Map[String,String]) extends StorageSource

case class DruidTimeStorage(id: String, source: DruidSource, partitioner: PlainTimePartitioner) extends TimeStorage[DruidSource, PlainTimePartitioner, TimeClient]

object DruidTimeStorage {
  implicit class DruidTimeStoragePimp(underlying: DruidTimeStorage) {
    def client(implicit driver: DruidClient) = new TimeClient {
      private val DruidTimeStorage(_, source, partitioner) = underlying

      def delete(partition: TimePartition): Future[Done] = Future.successful(Done.getInstance()) //deleting segments is not real-time, ie. delete & create of the same partition would not have deterministic outcome

      def markWithSuccess(partition: TimePartition): Future[Done] = Future.successful(Done.getInstance()) // done by druid

      def listAll: Future[Seq[TimePartition]] =
        list(new Interval(new DateTime(2015, 1, 1, 0, 0, 0, DateTimeZone.UTC), partitioner.granularity.truncate(new DateTime(DateTimeZone.UTC))))

      def list(range: Interval): Future[Seq[TimePartition]] = {
        Future {
          driver.forQueryingCoordinator(source.coordinator)(10.seconds, 1.minute)
            .listDataSourceIntervals(source.dataSource).get
            .getOrElse(Seq.empty)
            .flatMap( i => underlying.partitioner.granularity.getIterable(new Interval(i, ISOChronology.getInstanceUTC)) )
            .map( i => underlying.partitioner.build(i) )
            .filter(p => range.contains(p.value))
            .toSet[TimePartition].toVector
            .sortWith { case (x, y) => x.value.getStart.compareTo(y.value.getStart) < 0 }
        }(ExecutionContext.Implicits.global)
      }

    }
  }
}