package gwi.partitioner

import scala.concurrent.Future

trait StoragePartition[P] {
  def value: P
}

trait Pointer

trait StorageClient[IN,SP <: StoragePartition[IN]] {
  type OUT <: Pointer
  def delete(partition: SP): Unit
  def markWithSuccess(partition: SP): Unit
  def list: Future[Seq[SP]]
  def lookup(p: SP): OUT
}

trait StorageSource {
  def access: String
  def properties: Map[String,String]
}

trait Partitioner[IN, SP <: StoragePartition[IN]] {
  type OUT <: Pointer
  type S <: StorageSource
  def construct(p: SP, source: S): OUT
  def deconstruct(out: OUT): Option[SP]
  def buildPartition(p: String): SP
}

trait Storage[S <: StorageSource] {
  type IN
  type SP <: StoragePartition[IN]
  type PR <: Partitioner[IN,SP]
  type SC <: StorageClient[IN,SP]
  def id: String
  def source: S
  def partitioner: PR
}