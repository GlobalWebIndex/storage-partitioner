package gwi.partitioner.gcs

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes
import akka.stream.Materializer
import akka.stream.alpakka.googlecloud.storage.GoogleAuthConfiguration
import akka.stream.alpakka.googlecloud.storage.impl.GoogleCloudStorageClient
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import gwi.partitioner.{ObjectMetadata, S3Client}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class S3ClientGcsWrapper(gcsClient: GoogleCloudStorageClient) extends S3Client {

  override def exists(bucket: String, key: String): Future[Boolean] = {
    gcsClient.exists(bucket, key)
  }

  override def deleteObject(bucket: String, key: String): Future[Done] = {
    gcsClient.delete(bucket, key).transform {
      case Success(r) if r => Success(Done)
      case Success(_) => Failure(new IllegalArgumentException(s"Object not found: $bucket/$key"))
      case Failure(t) => Failure(t)
    }
  }

  override def putObject(
    bucket: String,
    key: String,
    data: Source[ByteString, _],
    contentLength: Long
  ): Future[Done] = {
    data.runWith(gcsClient.createUploadSink(bucket, key, ContentTypes.`text/plain(UTF-8)`))
      .map(_ => Done)
  }

  override def download(bucket: String, key: String): Source[ByteString, NotUsed] = {
    gcsClient.download(bucket, key).mapMaterializedValue(_ => NotUsed)
  }

  override def listBucket(bucket: String, prefix: Option[String]): Source[ObjectMetadata, NotUsed] = {
    gcsClient.listBucket(bucket, prefix).map { obj =>
      ObjectMetadata(
        bucket = bucket,
        key = obj.name,
        size = Some(obj.size),
        etag = obj.etag
      )
    }
  }

  override def multipartUpload(bucket: String, key: String, chunkSize: Option[Int]): Sink[ByteString, Future[Done]] = {
    gcsClient.createUploadSink(bucket, key, ContentTypes.`text/plain(UTF-8)`, chunkSize.getOrElse(5 * 1024 * 1024))
      .mapMaterializedValue(f => f.map(_ => Done))
  }
}

object S3ClientGcsWrapper {

  def apply(
    authConfiguration: GoogleAuthConfiguration
  )(implicit system: ActorSystem, mat: Materializer): S3ClientGcsWrapper = {
    S3ClientGcsWrapper(GoogleCloudStorageClient(authConfiguration))
  }

  def apply(googleCloudStorageClient: GoogleCloudStorageClient) = new S3ClientGcsWrapper(googleCloudStorageClient)
}
