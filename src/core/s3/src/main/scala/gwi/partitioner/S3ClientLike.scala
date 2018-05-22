package gwi.partitioner
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.s3.S3Settings
import akka.stream.alpakka.s3.impl.S3Headers
import akka.stream.alpakka.s3.scaladsl.{S3Client => AlpakkaS3Client}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class S3ClientLike(s3Client: AlpakkaS3Client) extends S3Client {

  override def exists(bucket: String, key: String): Future[Boolean] = {
    s3Client.getObjectMetadata(bucket, key).map(_.nonEmpty)
  }

  override def deleteObject(bucket: String, key: String): Future[Done] = {
    s3Client.deleteObject(bucket, key)
  }

  override def putObject(
    bucket: String,
    key: String,
    data: Source[ByteString, _],
    contentLength: Long
  ): Future[Done] = {
    s3Client.putObject(bucket, key, data, contentLength, s3Headers = S3Headers.empty).map(_ => Done)
  }

  override def download(bucket: String, key: String): Source[ByteString, NotUsed] = {
    s3Client.download(bucket, key)._1
  }

  override def listBucket(bucket: String, prefix: Option[String]): Source[ObjectMetadata, NotUsed] = {
    s3Client.listBucket(bucket, prefix).map { result =>
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
    chunkSize: Option[Int]
  ): Sink[ByteString, Future[Done]] = {
    s3Client.multipartUpload(bucket, key, chunkSize = chunkSize.getOrElse(AlpakkaS3Client.MinChunkSize))
      .mapMaterializedValue(f => f.map(_ => Done))
  }
}

object S3ClientLike {

  def apply()(implicit system: ActorSystem, mat: Materializer): S3ClientLike = {
    S3ClientLike(AlpakkaS3Client())
  }

  def apply(s3Settings: S3Settings)(implicit system: ActorSystem, mat: Materializer): S3ClientLike = {
    S3ClientLike(new AlpakkaS3Client(s3Settings))
  }

  def apply(s3Client: AlpakkaS3Client): S3ClientLike = new S3ClientLike(s3Client)
}
