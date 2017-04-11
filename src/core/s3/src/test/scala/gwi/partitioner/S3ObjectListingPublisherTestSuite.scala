package gwi.partitioner

import java.io.File

import akka.stream.scaladsl.Sink
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.Await
import scala.concurrent.duration._

class S3ObjectListingPublisherTestSuite extends FreeSpec with ScalaFutures with Matchers with S3Mock with AkkaSupport {
  implicit val futurePatience = PatienceConfig(Span(10, Seconds), Span(2, Seconds))

  private val bucket = "gwiq-views-t"
  private val basePath = "aws-toolkit/"
  private val testFile = File.createTempFile("test", ".txt")
  private val testKeys = (10 to 60).filter(_ % 10 == 0).map(_.toString).flatMap(root => (1 to 3).map(basePath + root + "/" + _ + "/" + testFile.getName))

  override def beforeAll(): Unit = try super.beforeAll() finally {
    startS3Container {
      testKeys.foreach( key => s3Driver.putObject(bucket, key, testFile) )
    }
  }

  override def afterAll(): Unit = try super.afterAll() finally {
    stopS3Container {
      testKeys.foreach(s3Driver.deleteObject(bucket,_))
    }
  }

  "publisher should list all keys with prefix" in {
    // TODO when this is fixed https://github.com/jubos/fake-s3/issues/191  ... test maxKeys=10
    val future = s3Driver.objSummarySource(bucket, basePath).runWith(Sink.seq)
    whenReady(future) { keys =>
      assertResult(18, "There are 18 keys overall, results we returned in 2 pages, concatenated by publisher")(keys.size)
    }
  }

  "published should list all dir names" in {
    val actual = Await.result(s3Driver.getRelativeDirPaths(bucket, basePath, 2, "/"), 1.minute)
    val expected = testKeys.map(_.split('/').slice(1,3)) // 10/01, 10/02, 10/03, etc...
    assertResult(testKeys.size)(actual.size)
    assertResult(expected.map(_.mkString("/")).toSet)(actual.map(_.mkString("/")).toSet)
  }

}
