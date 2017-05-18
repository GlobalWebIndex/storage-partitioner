package gwi.partitioner

import akka.Done
import akka.stream.ActorMaterializer
import akka.stream.alpakka.cassandra.scaladsl.CassandraSource
import akka.stream.scaladsl.Sink
import com.datastax.driver.core.Session
import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture}
import org.joda.time.Interval
import org.joda.time.chrono.ISOChronology

import scala.collection.JavaConverters._
import scala.collection.breakOut
import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.{Future, Promise}

case class CqlSource(contactPoints: Seq[String], access: String, tables: Set[String], meta: Set[String], properties: Map[String, String]) extends StorageSource

case class CqlTimeStorage(id: String, source: CqlSource, partitioner: PlainTimePartitioner) extends TimeStorage[CqlSource, PlainTimePartitioner, TimeClient]

object CqlTimeStorage {

  implicit final class GuavaFutureOpts[A](val guavaFut: ListenableFuture[A]) extends AnyVal {
    def asScala(): Future[A] = {
      val p = Promise[A]()
      val callback = new FutureCallback[A] {
        override def onSuccess(a: A): Unit = p.success(a)

        override def onFailure(err: Throwable): Unit = p.failure(err)
      }
      Futures.addCallback(guavaFut, callback)
      p.future
    }
  }

  implicit class CqlTimeStoragePimp(underlying: CqlTimeStorage) {
    def client(implicit session: Session, mat: ActorMaterializer) = new TimeClient {
      private val CqlTimeStorage(_, CqlSource(_, _, tables, meta, _), partitioner) = underlying

      private val pUpdateStatement = session.prepare(s"UPDATE partition SET tables = tables + ? WHERE interval=?;")
      private val pSelectStatement = session.prepare(s"SELECT * FROM partition;")
      private val pSelectStatementIn = session.prepare(s"SELECT * FROM partition WHERE interval IN ?;")

      def delete(partition: TimePartition): Future[Done] = Future.successful(Done) //TODO

      def markWithSuccess(partition: TimePartition): Future[Done] =
        session.executeAsync(pUpdateStatement.bind(meta.asJava, partition.value.toString)).asScala().map(_ => Done)(Implicits.global)

      def listAll: Future[Seq[TimePartition]] = {
        val javaTables = tables.asJava
        CassandraSource(pSelectStatement.bind())
          .collect { case row if row.getSet("tables", classOf[String]).containsAll(javaTables) =>
            underlying.partitioner.build(new Interval(row.getString("interval"), ISOChronology.getInstanceUTC))
          }.runWith(Sink.seq[TimePartition])
      }

      def list(range: Interval): Future[Seq[TimePartition]] = {
        val intervals: List[String] = partitioner.granularity.getIterable(range).map(_.toString)(breakOut)
        val javaTables = tables.asJava
        CassandraSource(pSelectStatementIn.bind(intervals.asJava))
          .collect { case row if row.getSet("tables", classOf[String]).containsAll(javaTables) =>
            underlying.partitioner.build(new Interval(row.getString("interval"), ISOChronology.getInstanceUTC))
          }.runWith(Sink.seq[TimePartition])
      }

      def listMissing(range: Interval): Future[Seq[TimePartition]] = {
        val intervals = partitioner.granularity.getIterable(range).toList
        list(range)
        .map { existingPartitions =>
          val existingIntervalSet = existingPartitions.map(_.value).toSet
          intervals.collect { case i if !existingIntervalSet.contains(i) =>
            underlying.partitioner.build(new Interval(i, ISOChronology.getInstanceUTC))
          }
        }(Implicits.global)
      }

    }
  }

}