package gwi.partitioner.gcs

import java.nio.file.Paths

import akka.stream.alpakka.googlecloud.storage.GoogleAuthConfiguration
import akka.stream.alpakka.googlecloud.storage.impl.GoogleCloudStorageClient
import gwi.partitioner.{AkkaSupport, S3Client, S3ClientSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class AlpakkaS3ClientGcsWrapperSpec extends S3ClientSpec with AkkaSupport {

  private[this] val gcsClient = {
    val path = Paths.get("/home/pribor/tmp/storage-partitioner-test/key.json")
    val config = GoogleAuthConfiguration(path)
    GoogleCloudStorageClient(config)
  }
  override protected[this] def s3Client: S3Client = AlpakkaGcsS3ClientWrapper(gcsClient)
  override protected[this] def bucket: String = "gwiq-storage-partitioner-test"

  override protected def beforeAll(): Unit = try super.beforeAll() finally {
    Await.ready(gcsClient.deleteFolder(bucket, ""), 10 seconds)
  }
}
