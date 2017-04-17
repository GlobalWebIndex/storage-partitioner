package gwi.partitioner

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class S3DriverSpec extends FreeSpec with FakeS3 with ScalaFutures with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  private val bucket = "gwiq-views-t"
  private val basePath = "S3DriverSpec/"

  private val testFile = File.createTempFile("test", ".txt")
  private val testFileContent = "lorem\nipsum"
  Files.write(testFile.toPath, testFileContent.getBytes(StandardCharsets.UTF_8))

  private val testKeys = (10 to 60).filter(_ % 10 == 0).map(_.toString).flatMap(root => (1 to 3).map(basePath + root + "/" + _ + "/" + testFile.getName))

  override def beforeAll(): Unit = try super.beforeAll() finally {
    startS3Container {
      s3Driver.createBucket(bucket)
      testKeys.foreach(key => s3Driver.putObject(bucket, key, testFile))
    }
  }

  override def afterAll(): Unit = try super.afterAll() finally {
    stopS3Container(testKeys.foreach(s3Driver.deleteObject(bucket,_)))
  }

  "should list all " - {

    "keys with prefix" in {
      val actual = s3Driver.listKeys(bucket, basePath)
      assertResult(18, "There are 18 keys overall, results we returned in 2 pages, concatenated by publisher")(actual.size)
    }

    "common prefixes" in {
      val expected = (10 to 60).filter(_ % 10 == 0).map(int => s"$basePath$int/").sorted
      val actual = s3Driver.commonPrefixSource(bucket, basePath, "/", 100).sorted
      assertResult(expected)(actual)
    }

    "relative dir paths" in {
      val actual = Await.result(s3Driver.getRelativeDirPaths(bucket, basePath, 2, "/"), 1.minute)
      val expected = testKeys.map(_.split('/').slice(1, 3)) // 10/01, 10/02, 10/03, etc...
      assertResult(testKeys.size)(actual.size)
      assertResult(expected.map(_.mkString("/")).toSet)(actual.map(_.mkString("/")).toSet)
    }

  }
}
