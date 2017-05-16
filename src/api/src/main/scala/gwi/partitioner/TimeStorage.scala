package gwi.partitioner

import org.joda.time.{DateTime, Interval}

import scala.concurrent.Future

class TimePartition(val value: Interval) extends StoragePartition[Interval]

trait TimeClient extends StorageClient[Interval,TimePartition] {
  def list(range: Interval): Future[Seq[TimePartition]]
}

trait TimePartitioner extends Partitioner[Interval] {
  type SP <: TimePartition
  def granularity: Granularity
  def build(i: Interval): SP
  def build(start: DateTime): SP
  def buildMany(i: Interval): Iterable[SP]
}

trait TimePartitionBuilders extends TimePartitioner {
  type SP = TimePartition
  def build(start: DateTime): TimePartition = new TimePartition(granularity.bucket(start))
  def build(i: Interval): TimePartition = {
    require(granularity.getIterable(i).size == 1)
    build(i.getStart)
  }
  def buildMany(i: Interval): Iterable[TimePartition] = granularity.getIterable(i).map(build)
}

case class PlainTimePartitioner(granularity: Granularity) extends TimePartitioner with TimePartitionBuilders

trait TimeStorage[S <: StorageSource, TPR <: TimePartitioner, C <: TimeClient] extends Storage[S] {
  type IN = Interval
  type PR = TPR
  type SP = TimePartition
  type SC = C
}

object TimeStorage {
  type * = TimeStorage[_ <: StorageSource,_ <: TimePartitioner,_ <: TimeClient]
}