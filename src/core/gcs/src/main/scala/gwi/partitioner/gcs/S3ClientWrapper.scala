package gwi.partitioner.gcs

import akka.stream.alpakka.googlecloud.storage.impl.GoogleCloudStorageClient

class S3ClientWrapper(googleCloudStorageClient: GoogleCloudStorageClient) {

}

object S3ClientWrapper {
  def apply(googleCloudStorageClient: GoogleCloudStorageClient) = new S3ClientWrapper(googleCloudStorageClient)
}
