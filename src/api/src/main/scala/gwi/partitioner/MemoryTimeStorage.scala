package gwi.partitioner

import org.joda.time.Interval

import scala.concurrent.Future
import scala.language.implicitConversions

case class MemorySource(access: String, partitions: Seq[TimePartition], properties: Map[String, String]) extends StorageSource
case class MemoryTimeStorage(id: String, source: MemorySource, partitioner: IdentityTimePartitioner) extends TimeStorage[MemorySource,IdentityTimePartitioner,TimeClient]

object MemoryTimeStorage {
  implicit class MemoryTimeStoragePimp(underlying: MemoryTimeStorage) {
    def client = new TimeClient {
      override type OUT = IdentityPointer
      private var state: Map[TimePartition, Boolean] = underlying.source.partitions.map(_ -> true).toMap
      def delete(partition: TimePartition): Unit = state = state - partition
      def markWithSuccess(partition: TimePartition): Unit = state = state.updated(partition, true)
      def list: Future[Seq[TimePartition]] = Future(state.keys.toSeq)(ExeC.global)
      def list(range: Interval): Future[Seq[TimePartition]] = list.map(_.filter(p => range.contains(p.value)))(ExeC.sameThread)
      def lookup(p: TimePartition): IdentityPointer = underlying.partitioner.construct(p, underlying.source)
    }
  }
}