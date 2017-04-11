package gwi.partitioner

import scala.concurrent.Future

trait StoragePartition[P] {
  def value: P
}

trait Pointer

trait StorageClient[IN,SP <: StoragePartition[IN]] {
  def delete(partition: SP): Unit
  def markWithSuccess(partition: SP): Unit
  def list: Future[Seq[SP]]
}

trait StorageSource {
  def access: String
  def properties: Map[String,String]
}

trait Partitioner[IN, SP <: StoragePartition[IN]] {
  type OUT <: Pointer
  def deconstruct(out: OUT): Option[SP]
  def buildPartition(p: String): SP
}

trait Storage[S <: StorageSource] {
  type OUT <: Pointer
  type IN
  type SP <: StoragePartition[IN]
  type PR <: Partitioner[IN,SP]
  type SC <: StorageClient[IN,SP]
  def id: String
  def source: S
  def partitioner: PR
  def lift(p: SP): OUT
}