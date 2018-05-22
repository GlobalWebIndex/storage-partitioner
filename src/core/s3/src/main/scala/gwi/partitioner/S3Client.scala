package gwi.partitioner

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.Future

trait S3Client {

  /**
    * Checks if an Object exists
    *
    * @param bucket the bucket name
    * @param key the object key
    * @return A [[Future]] containing a false in case the object does not exist
    */
  def exists(bucket: String, key: String): Future[Boolean]

  /**
    * Deletes an Object
    *
    * @param bucket the bucket name
    * @param key the object key
    * @return A [[Future]] of [[Done]]
    */
  def deleteObject(bucket: String, key: String): Future[Done]

  /**
    * Uploads an Object, use this for small files and [[ObjectMetadata]] for bigger ones
    *
    * @param bucket the bucket name
    * @param key the object key
    * @param data a [[Stream]] of [[ByteString]]
    * @param contentLength the number of bytes that will be uploaded (required!)
    * @return a [[Future]] containing the [[ObjectMetadata]] of the uploaded Object
    */
  def putObject(bucket: String,
    key: String,
    data: Source[ByteString, _],
    contentLength: Long): Future[Done]

  /**
    * Downloads an Object
    *
    * @param bucket the bucket name
    * @param key the object key
    * @return A [[Source]] of [[ByteString]], and a [[Future]] containing the [[ObjectMetadata]]
    */
  def download(bucket: String, key: String): Source[ByteString, NotUsed]

  /**
    * Will return a source of object metadata for a given bucket with optional prefix.
    * This will automatically page through all keys with the given parameters.
    *
    * @param bucket Which bucket that you list object metadata for
    * @param prefix Prefix of the keys you want to list under passed bucket
    * @return [[Source]] of [[ObjectMetadata]]
    */
  def listBucket(bucket: String, prefix: Option[String]): Source[ObjectMetadata, NotUsed]

  /**
    * Uploads aa Object by making multiple requests
    *
    * @param bucket the bucket name
    * @param key the object key
    * @param chunkSize the size of the requests sent to a storage
    * @return a [[Sink]] that accepts [[ByteString]]'s and materializes to a [[Future]] of [[ObjectMetadata]]
    */
  def multipartUpload(bucket: String,
    key: String,
    chunkSize: Option[Int] = None): Sink[ByteString, Future[Done]]
}

final case class ObjectMetadata(
  bucket: String,
  key: String,
  size: Option[String],
  etag: String
)
