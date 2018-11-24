package gwi.partitioner.gcs

import java.io.File
import java.nio.file.{Files, Paths, StandardOpenOption}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentType, ContentTypes}
import akka.stream.Materializer
import akka.stream.alpakka.googlecloud.storage.GoogleAuthConfiguration
import akka.stream.alpakka.googlecloud.storage.impl.GoogleCloudStorageClient
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import gwi.partitioner.{BlobStorageClient, ObjectMetadata}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class AlpakkaGcsClient(gcsClient: GoogleCloudStorageClient)(implicit materializer: Materializer) extends BlobStorageClient {

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

  override def multipartUpload(
    bucket: String,
    key: String,
    chunkSize: Option[Int] = None,
    contentType: ContentType = ContentTypes.`application/octet-stream`
  ): Sink[ByteString, Future[Done]] = {
    gcsClient.createUploadSink(bucket, key, contentType, chunkSize.getOrElse(5 * 1024 * 1024))
      .mapMaterializedValue(f => f.map(_ => Done))
  }
}

object AlpakkaGcsClient {

  val googleAppCredentials      = "GOOGLE_APPLICATION_CREDENTIALS"
  val googleAppCredentialsPath  = "GOOGLE_APPLICATION_CREDENTIALS_PATH"

  def getGoogleAuthConf: GoogleAuthConfiguration =
    credentialsPath
      .filter(Paths.get(_).toFile.exists())
      .map( path => GoogleAuthConfiguration(Paths.get(path)))
      .getOrElse {
        sys.env.get(googleAppCredentials).map { credentials =>
          val tempFile = File.createTempFile("cread-", ".json")
          try {
            Files.write(tempFile.toPath, credentials.getBytes, StandardOpenOption.WRITE)
            GoogleAuthConfiguration(tempFile.toPath)
          } finally tempFile.delete()
        }.getOrElse(throw new Exception(s"No key found in $googleAppCredentialsPath env and no $googleAppCredentials env var exported !"))
      }

  def apply(
    authConfiguration: GoogleAuthConfiguration
  )(implicit system: ActorSystem, mat: Materializer): AlpakkaGcsClient = {
    AlpakkaGcsClient(GoogleCloudStorageClient(authConfiguration))
  }

  def apply(
    googleCloudStorageClient: GoogleCloudStorageClient
  )(implicit materializer: Materializer): AlpakkaGcsClient = {
    new AlpakkaGcsClient(googleCloudStorageClient)
  }

  def apply()(implicit system: ActorSystem, mat: Materializer): AlpakkaGcsClient =
    AlpakkaGcsClient(getGoogleAuthConf)

  def credentialsPath: Option[String] = sys.env.get(googleAppCredentialsPath)
}
