package gwi.partitioner.gcs

import java.nio.file.Paths

import akka.stream.alpakka.googlecloud.storage.GoogleAuthConfiguration
import akka.stream.alpakka.googlecloud.storage.impl.GoogleCloudStorageClient
import gwi.partitioner.{AkkaSupport, S3Client, S3ClientSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class AlpakkaS3ClientGcsWrapperSpec extends S3ClientSpec with AkkaSupport {
  import AlpakkaS3ClientGcsWrapperSpec._

  private[this] lazy val gcsClient = {
    val path = Paths.get(getKeyPath.getOrElse(throw new Exception("No key found!")))
    val config = GoogleAuthConfiguration(path)
    GoogleCloudStorageClient(config)
  }

  override protected[this] def s3Client: S3Client = AlpakkaGcsS3ClientWrapper(gcsClient)

  override protected[this] def bucket: String = "gwiq-storage-partitioner-test"

  override protected def beforeAll(): Unit = try super.beforeAll() finally {
    Await.ready(gcsClient.deleteFolder(bucket, ""), 10.seconds)
  }

  override protected[this] def ignore: Boolean = getKeyPath.isEmpty

  private[this] def getKeyPath = sys.env.get(googleAppCredetials)
}

object AlpakkaS3ClientGcsWrapperSpec {
  val googleAppCredetials = "GOOGLE_APPLICATION_CREDENTIALS"
}
