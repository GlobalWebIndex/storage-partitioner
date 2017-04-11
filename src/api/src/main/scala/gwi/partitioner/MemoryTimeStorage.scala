package gwi.partitioner

import org.joda.time.Interval

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

case class MemorySource(access: String, partitions: Seq[TimePartition], properties: Map[String, String]) extends StorageSource
case class MemoryTimeStorage(id: String, source: MemorySource, partitioner: IdentityTimePartitioner) extends TimeStorage[MemorySource,IdentityTimePartitioner,TimeClient] {
  type OUT = IdentityPointer
  def lift(p: TimePartition): IdentityPointer = IdentityPointer(p.value.toString)
}

object MemoryTimeStorage {
  implicit class MemoryTimeStoragePimp(underlying: MemoryTimeStorage) {
    def client = new TimeClient {
      private var state: Map[TimePartition, Boolean] = underlying.source.partitions.map(_ -> true).toMap
      def delete(partition: TimePartition): Unit = state = state - partition
      def markWithSuccess(partition: TimePartition): Unit = state = state.updated(partition, true)
      def list: Future[Seq[TimePartition]] = Future(state.keys.toSeq)(ExecutionContext.Implicits.global)
      def list(range: Interval): Future[Seq[TimePartition]] = list.map(_.filter(p => range.contains(p.value)))(ExecutionContext.Implicits.global)
    }
  }
}