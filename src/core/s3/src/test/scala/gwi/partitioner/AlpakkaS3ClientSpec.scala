package gwi.partitioner

class AlpakkaS3ClientSpec extends S3ClientSpec with FakeS3 {

  override protected[this] def bucket: String = "testbucket"

  override def beforeAll(): Unit = try super.beforeAll() finally {
    startS3 {
      legacyClient.createBucket(bucket)
      Thread.sleep(500)
    }
  }

  override def afterAll(): Unit = try super.afterAll() finally {
    stopS3(())
  }
}
