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

case class CqlSource(access: String, tag: String, meta: Set[String], properties: Map[String, String]) extends StorageSource

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

  trait CqlTimeClient extends TimeClient {
    def listMissing(range: Interval): Future[Seq[TimePartition]]
  }

  implicit class CqlTimeStoragePimp(underlying: CqlTimeStorage) {
    def client(implicit session: Session, mat: ActorMaterializer): CqlTimeClient = new CqlTimeClient {
      private val CqlTimeStorage(_, source, partitioner) = underlying

      private val tag = source.tag

      private val pAddStatement       = session.prepare("INSERT INTO partition (interval,tag,start,end) VALUES (?,?,?,?)")
      private val pSelectStatement    = session.prepare("SELECT interval FROM partition WHERE tag = ? ALLOW FILTERING")
      private val pSelectStatementIn  = session.prepare("SELECT interval FROM partition WHERE interval IN ? AND tag = ?")
      private val pRemoveStatementIn  = session.prepare("DELETE FROM partition WHERE interval = ? AND tag = ?")

      def delete(partition: TimePartition): Future[Done] =
        session.executeAsync(
          pRemoveStatementIn.bind(partition.value.toString, tag)
        ).asScala().map(_ => Done)(Implicits.global)

      def markWithSuccess(partition: TimePartition): Future[Done] =
        session.executeAsync(
          pAddStatement.bind(partition.value.toString, tag, partition.value.getStart.toDate, partition.value.getEnd.toDate)
        ).asScala().map(_ => Done)(Implicits.global)

      def listAll: Future[Seq[TimePartition]] = {
        CassandraSource(pSelectStatement.bind(tag))
          .map ( row  => underlying.partitioner.build(new Interval(row.getString("interval"), ISOChronology.getInstanceUTC)) )
          .runWith(Sink.seq[TimePartition])
      }

      def list(range: Interval): Future[Seq[TimePartition]] = {
        val intervals: List[String] = partitioner.granularity.getIterable(range).map(_.toString)(breakOut)
        CassandraSource(pSelectStatementIn.bind(intervals.asJava, tag))
          .map ( row => underlying.partitioner.build(new Interval(row.getString("interval"), ISOChronology.getInstanceUTC)) )
          .runWith(Sink.seq[TimePartition])
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