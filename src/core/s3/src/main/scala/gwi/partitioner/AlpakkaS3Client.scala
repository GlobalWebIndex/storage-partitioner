package gwi.partitioner
import akka.http.scaladsl.model.{ContentType, ContentTypes}
import akka.stream.alpakka.s3.S3Headers
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AlpakkaS3Client extends BlobStorageClient {

  override def exists(bucket: String, key: String): Source[Boolean, NotUsed] = {
    S3.getObjectMetadata(bucket, key).map(_.nonEmpty)
  }

  override def deleteObject(bucket: String, key: String): Source[Boolean, NotUsed] = {
    S3.deleteObject(bucket, key).map(_ => true)
  }

  override def putObject(
    bucket: String,
    key: String,
    data: Source[ByteString, _],
    contentLength: Long
  ): Source[Done, NotUsed] = {
    S3.putObject(bucket, key, data, contentLength, s3Headers = S3Headers.create).map(_ => Done)
  }

  override def download(bucket: String, key: String): Source[Option[Source[ByteString, NotUsed]], NotUsed] = {
    S3.download(bucket, key).map(_.map(_._1))
  }

  override def listBucket(bucket: String, prefix: Option[String]): Source[ObjectMetadata, NotUsed] = {
    S3.listBucket(bucket, prefix).map { result =>
      ObjectMetadata(
        bucket = result.bucketName,
        key = result.key,
        size = Some(result.size.toString),
        etag = result.eTag
      )
    }
  }

  override def multipartUpload(
    bucket: String,
    key: String,
    chunkSize: Option[Int] = None,
    contentType: ContentType = ContentTypes.`application/octet-stream`
  ): Sink[ByteString, Future[Done]] = {
    S3.multipartUpload(bucket, key, contentType)
      .mapMaterializedValue(f => f.map(_ => Done))
  }
}
