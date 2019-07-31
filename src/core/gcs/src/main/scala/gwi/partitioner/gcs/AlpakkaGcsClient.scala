package gwi.partitioner.gcs

import akka.http.scaladsl.model.{ContentType, ContentTypes}
import akka.stream.alpakka.googlecloud.storage.scaladsl.GCStorage
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import gwi.partitioner.{BlobStorageClient, ObjectMetadata}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AlpakkaGcsClient extends BlobStorageClient {

  override def exists(bucket: String, key: String): Source[Boolean, NotUsed] = {
    GCStorage.getObject(bucket, key).map(_.nonEmpty)
  }

  override def deleteObject(bucket: String, key: String): Source[Boolean, NotUsed] = {
    GCStorage.deleteObject(bucket, key)
  }

  override def putObject(
    bucket: String,
    key: String,
    data: Source[ByteString, _],
    contentLength: Long
  ): Source[Done, NotUsed] = {
    GCStorage.simpleUpload(bucket, key, data, ContentTypes.`text/plain(UTF-8)`)
      .map(_ => Done)
  }

  override def download(bucket: String, key: String): Source[Option[Source[ByteString, NotUsed]], NotUsed] = {
    GCStorage.download(bucket, key).mapMaterializedValue(_ => NotUsed)
  }

  override def listBucket(bucket: String, prefix: Option[String]): Source[ObjectMetadata, NotUsed] = {
    GCStorage.listBucket(bucket, prefix).map { obj =>
      ObjectMetadata(
        bucket,
        key = obj.name,
        size = Some(obj.size.toString),
        etag = obj.etag
      )
    }
  }

  override def multipartUpload(
    bucket: String,
    key: String,
    chunkSize: Option[Int] = None,
    contentType: ContentType = ContentTypes.`application/octet-stream`
  ): Sink[ByteString, Future[Done]] = {
    GCStorage.resumableUpload(bucket, key, contentType, chunkSize.getOrElse(5 * 1024 * 1024))
      .mapMaterializedValue(f => f.map(_ => Done))
  }
}
