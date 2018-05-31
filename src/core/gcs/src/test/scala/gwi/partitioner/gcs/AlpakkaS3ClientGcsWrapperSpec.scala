package gwi.partitioner.gcs

import gwi.partitioner.{AkkaSupport, S3Client, S3ClientSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class AlpakkaS3ClientGcsWrapperSpec extends S3ClientSpec with AkkaSupport {

  override protected[this] def s3Client: S3Client = AlpakkaGcsS3ClientWrapper()

  override protected[this] def bucket: String = "gwiq-storage-partitioner-test"

  override protected def beforeAll(): Unit = try super.beforeAll() finally {
    val res = s3Client.listBucket(bucket).runForeach { o =>
      s3Client.deleteObject(o.bucket, o.key)
    }
    Await.ready(res, 10.seconds)
  }

  override protected[this] def ignore: Boolean = AlpakkaGcsS3ClientWrapper.credentialsPath.isEmpty
}
