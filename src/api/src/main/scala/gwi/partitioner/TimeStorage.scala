package gwi.partitioner

import org.joda.time.Interval
import org.joda.time.chrono.ISOChronology

import scala.concurrent.Future

case class TimePartition(value: Interval) extends StoragePartition[Interval]

trait TimeClient extends StorageClient[Interval,TimePartition] {
  def list(range: Interval): Future[Seq[TimePartition]]
}

trait TimePartitioner extends Partitioner[Interval,TimePartition] {
  def granularity: Granularity
  def buildPartitions(p: Interval): Iterable[TimePartition] =
    granularity.getIterable(p).map(buildPartition)
  def buildPartition(p: Interval): TimePartition =
    TimePartition(p)
  def buildPartition(p: String): TimePartition =
    TimePartition(new Interval(p, ISOChronology.getInstanceUTC))
}

case class IdentityPointer(value: String) extends Pointer

case class IdentityTimePartitioner(granularity: Granularity) extends TimePartitioner {
  type OUT = IdentityPointer
  type S = StorageSource
  def deconstruct(p: IdentityPointer) = Option(buildPartition(p.value))
}

trait TimeStorage[S <: StorageSource, TPR <: TimePartitioner, C <: TimeClient] extends Storage[S] {
  type IN = Interval
  type PR = TPR
  type SP = TimePartition
  type SC = C
}

object TimeStorage {
  type * = TimeStorage[_ <: StorageSource,_ <: TimePartitioner,_ <: TimeClient]
}