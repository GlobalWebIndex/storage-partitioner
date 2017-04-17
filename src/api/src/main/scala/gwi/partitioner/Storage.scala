package gwi.partitioner

import scala.concurrent.Future

trait StoragePartition[P] {
  def value: P
}

trait StorageClient[IN,SP <: StoragePartition[IN]] {
  def delete(partition: SP): Unit
  def markWithSuccess(partition: SP, content: String): Unit
  def list: Future[Seq[SP]]
}

trait StorageSource {
  def access: String
  def properties: Map[String,String]
}

trait Partitioner[IN] {
  type SP <: StoragePartition[IN]
}

trait Storage[S <: StorageSource] {
  type IN
  type SP <: StoragePartition[IN]
  type PR <: Partitioner[IN]
  type SC <: StorageClient[IN,SP]
  def id: String
  def source: S
  def partitioner: PR
}