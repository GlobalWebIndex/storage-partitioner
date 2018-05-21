package gwi.partitioner.gcs

import akka.stream.alpakka.googlecloud.storage.impl.GoogleCloudStorageClient
import akka.stream.alpakka.s3.scaladsl.S3Client

class S3ClientWrapper(googleCloudStorageClient: GoogleCloudStorageClient) extends S3Client {

}

object S3ClientWrapper {
  def apply(googleCloudStorageClient: GoogleCloudStorageClient) = new S3ClientWrapper(googleCloudStorageClient)
}
