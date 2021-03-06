package gwi.partitioner

import akka.stream.scaladsl.{Compression, Sink, Source}
import akka.util.ByteString
import org.scalactic.source.Position
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

trait S3ClientSpec extends FreeSpec with Matchers with ScalaFutures with AkkaSupport {
  import S3ClientSpec._

  protected[this] def s3Client: BlobStorageClient
  protected[this] def bucket: String

  protected[this] def ignore(name: String): Boolean = false

  protected[this] implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(10, Millis))

  "S3 client should" - {
    "check if an object exists" inIgnorable {
      val res = for {
        f <- uploadFile()
        exists <- s3Client.exists(bucket, f.name).runWith(Sink.head)
      } yield exists

      res.futureValue shouldBe true
    }

    "check if an object doesn't exist" inIgnorable {
      val res = s3Client.exists(bucket, "not-exists").runWith(Sink.head)
      res.futureValue shouldBe false
    }

    "delete an object" inIgnorable {
      val res = for {
        f <- uploadFile()
        _ <- s3Client.deleteObject(bucket, f.name).runWith(Sink.head)
        exists <- s3Client.exists(bucket, f.name).runWith(Sink.head)
      } yield exists

      res.futureValue shouldBe false
    }

    "put an object" inIgnorable {
      val f = genFile()
      val res = for {
        _ <- s3Client.putObject(bucket, f.name, Source.single(f.content), f.content.size).runWith(Sink.head)
        exists <- s3Client.exists(bucket, f.name).runWith(Sink.head)
      } yield exists

      res.futureValue shouldBe true
    }

    "download an object" inIgnorable {
      val res = for {
        uploaded <- uploadFile()
        downloaded <- s3Client.download(bucket, uploaded.name).map(_.get.runFold(ByteString.empty)(_ ++ _)).runWith(Sink.head)
      } yield (uploaded, downloaded)

      val (uploaded, downloaded) = res.futureValue
      downloaded shouldEqual uploaded.content
    }

    "list a bucket" inIgnorable {
      val res = for {
        before <- s3Client.listBucket(bucket).runFold(Seq(): Seq[ObjectMetadata])((s, o) => o +: s)
        _ <- uploadFile()
        after <- s3Client.listBucket(bucket).runFold(Seq(): Seq[ObjectMetadata])((s, o) => o +: s)
      } yield (before, after)

      val (before, after) = res.futureValue
      before.size shouldEqual (after.size - 1)
    }

    "upload an object by making multiple requests" inIgnorable {
      val f = genFile()
      val res = for {
        _ <- Source.single(f.content)
          .via(Compression.gzip)
          .runWith(s3Client.multipartUpload(bucket, f.name))
        downloaded <- s3Client.download(bucket, f.name).map(_.get.via(Compression.gunzip()).runFold(ByteString.empty)(_ ++ _)).runWith(Sink.head)
      } yield downloaded

      res.futureValue shouldEqual f.content
    }
  }

  protected[this] def uploadFile(): Future[File] = {
    val f = genFile()
    s3Client.putObject(bucket, f.name, Source.single(f.content), f.content.size)
      .map(_ => f).runWith(Sink.head)
  }

  protected[this] def genFile(): File = {
    val filename = Random.alphanumeric.take(20).mkString
    val content = ByteString(Random.alphanumeric.take(5000).mkString)
    File(filename, content)
  }

  implicit class StringExt(wordSpecStringWrapper: String) {
    def inIgnorable(f: => Any /* Assertion */ )(implicit pos: Position): Unit =
      if (ignore(wordSpecStringWrapper)) {
        wordSpecStringWrapper.ignore(f)
      } else {
        wordSpecStringWrapper.in(f)
      }
  }
}

object S3ClientSpec {
  case class File(name: String, content: ByteString)
}
