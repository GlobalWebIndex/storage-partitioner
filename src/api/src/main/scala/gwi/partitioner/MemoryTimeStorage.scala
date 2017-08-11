package gwi.partitioner

import akka.Done
import org.joda.time.Interval

import scala.concurrent.{ExecutionContext, Future}

case class MemorySource(access: String, meta: Set[String], partitions: Seq[TimePartition], properties: Map[String, String]) extends StorageSource
case class MemoryTimeStorage(id: String, source: MemorySource, partitioner: PlainTimePartitioner) extends TimeStorage[MemorySource,PlainTimePartitioner,TimeClient]

object MemoryTimeStorage {
  implicit class MemoryTimeStoragePimp(underlying: MemoryTimeStorage) {
    def client = new TimeClient {
      private var state: Map[TimePartition, Boolean] = underlying.source.partitions.map(_ -> true).toMap
      def delete(partition: TimePartition): Future[Done] = {
        state = state - partition
        Future.successful(Done.getInstance())
      }
      def markWithSuccess(partition: TimePartition): Future[Done] = {
        state = state.updated(partition, true)
        Future.successful(Done.getInstance())
      }
      def listAll: Future[Seq[TimePartition]] = Future(state.keys.toSeq)(ExecutionContext.Implicits.global)
      def list(range: Interval): Future[Seq[TimePartition]] = listAll.map(_.filter(p => range.contains(p.value)))(ExecutionContext.Implicits.global)
    }
  }
}