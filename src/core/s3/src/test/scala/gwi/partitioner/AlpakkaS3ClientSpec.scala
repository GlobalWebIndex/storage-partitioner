package gwi.partitioner

class AlpakkaS3ClientSpec extends S3ClientSpec with FakeS3 {

  override protected[this] def bucket: String = "testbucket"

  override def beforeAll(): Unit = try super.beforeAll() finally {
    legacyClient.createBucket(bucket)
    Thread.sleep(500)
  }

  override protected[this] def ignore(name: String): Boolean = {
    name == "upload an object by making multiple requests"
  }
}
