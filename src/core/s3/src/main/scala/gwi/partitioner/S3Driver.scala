package gwi.partitioner

import java.io.ByteArrayInputStream
import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import javax.xml.bind.DatatypeConverter._

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.stream.Materializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.alpakka.s3.auth
import akka.stream.alpakka.s3.scaladsl.S3Client
import akka.stream.scaladsl.{Sink, Source}
import com.amazonaws.auth.{AWSCredentials, BasicAWSCredentials}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{S3ObjectSummary, _}
import com.amazonaws.{ClientConfiguration, ClientConfigurationFactory}
import monix.eval.Task
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.UncaughtExceptionReporter
import monix.execution.schedulers.AsyncScheduler

import scala.annotation.tailrec
import scala.collection.mutable.Builder
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

class S3Driver(credentials: AWSCredentials, region: Region, config: ClientConfiguration)(implicit system: ActorSystem, val mat: Materializer) extends AmazonS3Client(credentials, config) {
  lazy val alpakka = new S3Client(auth.AWSCredentials(credentials.getAWSAccessKeyId, credentials.getAWSSecretKey), region.getName)
  setRegion(region)
}

object S3Driver {
  def apply(id: String, key: String, region: String, config: ClientConfiguration = new ClientConfigurationFactory().getConfig)(implicit system: ActorSystem, mat: Materializer): S3Driver =
    new S3Driver(new BasicAWSCredentials(id, key), Region.getRegion(Regions.fromName(region)), config)

  private def friendlyCachedThreadPoolExecutor(name: String, corePoolFactor: Int, maximumPoolFactor: Int, keepAliveTime: Int) = {
    def availableProcessors = Runtime.getRuntime.availableProcessors
    def daemonThreadFactory = new ThreadFactory {
      private val count = new AtomicInteger()
      override def newThread(r: Runnable): Thread = {
        val thread = new Thread(r)
        thread.setName(s"$name-${count.incrementAndGet}")
        thread.setDaemon(true)
        thread
      }
    }

    val pool = new ThreadPoolExecutor(
      corePoolFactor * availableProcessors,
      maximumPoolFactor * availableProcessors,
      keepAliveTime,
      TimeUnit.SECONDS,
      new SynchronousQueue[Runnable](),
      daemonThreadFactory
    )
    pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy)
    pool
  }

  lazy val cachedScheduler =
    AsyncScheduler(
      Executors.newSingleThreadScheduledExecutor(),
      ExecutionContext.fromExecutorService(friendlyCachedThreadPoolExecutor("s3Driver", 4, 20, 60)),
      UncaughtExceptionReporter.LogExceptionsToStandardErr,
      AlwaysAsyncExecution
    )

  case class Attempt(n: Int, sleep_s: Int)
  case class S3PutException(msg: String, optCause: Option[Throwable] = None) extends Exception(msg, optCause.orNull)

  implicit class S3Pimp[C <: AmazonS3Client](underlying: C) {

    def readObjectStream[T](bucketName: String, key: String)(fn: (S3ObjectInputStream) => T): T = {
      val s3Obj = underlying.getObject(bucketName, key)
      try fn(s3Obj.getObjectContent) finally Try(s3Obj.close())
    }

    def readObjectStreamAsString(bucketName: String, key: String, expectedSize: Int): String =
      readObjectStream(bucketName, key)( is => IO.streamToString(is, expectedSize))

    def readObjectGZipStreamAsString(bucketName: String, key: String, expectedSize: Int): String =
      readObjectStream(bucketName, key)( is => IO.streamToString(new GZIPInputStream(is), expectedSize))

    def readObjectStreamLines(bucketName: String, key: String, expectedSize: Int): Array[String] =
      readObjectStream(bucketName, key)( is => IO.streamToSeq(is, expectedSize))

    def readObjectGZipStreamLines(bucketName: String, key: String, expectedSize: Int): Array[String] =
      readObjectStream(bucketName, key)( is => IO.streamToSeq(new GZIPInputStream(is), expectedSize))

    def listKeys(bucketName: String, prefix: String): Vector[String] = {
      import scala.collection.JavaConverters._
      @tailrec
      def recursively(listing: ObjectListing, summaries: Builder[String, Vector[String]]): Builder[String, Vector[String]] = {
        summaries ++= listing.getObjectSummaries.asScala.map(_.getKey)
        if (listing.isTruncated) {
          recursively(underlying.listNextBatchOfObjects(listing), summaries)
        } else {
          summaries
        }
      }
      recursively(underlying.listObjects(bucketName, prefix), Vector.newBuilder[String]).result()
    }

    def commonPrefixSource(bucket: String, prefix: String, delimiter: String, expectedSize: Int): Seq[String] = {
      @tailrec
      def recursively(req: ListObjectsRequest, listing: ObjectListing, result: util.List[String]): util.List[String] = {
        result.addAll(listing.getCommonPrefixes)
        if (listing.isTruncated) {
          req.setMarker(listing.getNextMarker)
          recursively(req, underlying.listObjects(req), result)
        } else {
          result
        }
      }
      import scala.collection.JavaConverters._
      val slashEndingDirPath = if (prefix.endsWith(delimiter)) prefix else prefix + delimiter // commonPrefixes method expects prefix to end with delimiter
      val req = new ListObjectsRequest().withBucketName(bucket).withPrefix(slashEndingDirPath).withDelimiter(delimiter)
      recursively(req, underlying.listObjects(req), new util.ArrayList[String](expectedSize)).asScala
    }

    /**
      * Purpose of this method is listing logical partitions of s3 storage, For instance TimeSeries data is stored in this hierarchy 2015/01/01/01
      * and if we needed to see whether there is some hour in a year missing we can use this list and compare it to list of all hours in year
      *
      * @param prefix    of directory that we want to list directory paths of. Must end by delimiter !!!
      * @param atLevel   level at which we want to list directory paths, ie. to list hour paths like 2015/01/01/01 we need level 4
      * @param delimiter of directories, usually slash
      * @return seq of directory name sequences at a certain level : Seq(Seq(2015, 01, 01, 01), Seq(2015, 01, 01, 02), Seq(2015, 01, 01, 03))
      */
    def getRelativeDirPaths(bucket: String, prefix: String, atLevel: Int, delimiter: String): Future[Seq[Seq[String]]] = {
      def listPrefixes(pref: String) = Task.fork(Task.eval(commonPrefixSource(bucket, pref, delimiter, 100)), cachedScheduler).asyncBoundary

      def recursively(prefixesF: Task[Seq[String]]): Task[Seq[String]] = {
        prefixesF.flatMap {
          case prefixes if prefixes.isEmpty =>
            Task.eval(Seq.empty)
          case prefixes =>
            recursively(Task.gather(prefixes.map(listPrefixes)).map(_.flatten)).map(prefixes ++ _)
        }
      }
      recursively(listPrefixes(prefix)).map { dirNames =>
        dirNames
          .map(_.substring(prefix.length).split(delimiter).filter(_.nonEmpty))
          .collect {
            case arr if arr.length == atLevel => arr.toSeq
          }
      }.runAsync(monix.execution.Scheduler.Implicits.global)
    }

    /**
      * Rock solid s3 put method that is avoiding common bandwidth problems
      * leading to socket connection closed exceptions and mismatching md5 hashes
      * by attempting to put s3 object multiple times until it succeeds
      */
    def putUntilSuccess(bucket: String, bucketPath: String, data: Array[Byte], md5: Option[String], attempt: Attempt = Attempt(5, 5)): Try[PutObjectResult] = {
      val metaData = new ObjectMetadata()
      metaData.setContentLength(data.length)
      md5.foreach(metaData.setContentMD5)
      Try(underlying.putObject(bucket, bucketPath, new ByteArrayInputStream(data), metaData)) match {
        case s@Success(result) if util.Arrays.equals(parseBase64Binary(result.getContentMd5), parseHexBinary(result.getETag)) =>
          s
        case failed if attempt.n > 0 =>
          putUntilSuccess(bucket, bucketPath, data, md5, attempt.copy(n = attempt.n-1))
        case Success(result) =>
          Try(underlying.deleteObject(bucket, bucketPath))
          Failure(S3PutException(s"Unable to copy resource to $bucket:$bucketPath due to repeated md5 hash mismatch !!!"))
        case Failure(ex) =>
          Failure(S3PutException(s"Unable to copy resource to $bucket:$bucketPath due to unexpected s3 error", Option(ex)))

      }
    }

    def getDirFileNames(bucket: String, dirPath: String, regex: Regex)(implicit m: Materializer): Future[Seq[String]] = {
      val slashEndingDirPath = if (dirPath.endsWith("/")) dirPath else dirPath + "/"  // let's avoid users listing anything else than directories, it's dangerous
      objSummarySource(bucket, slashEndingDirPath)
        .map(_.getKey.split("/").last)
        .filter(regex.pattern.matcher(_).matches())
        .runWith(Sink.seq)
    }

    /**
      * @note that s3 storage considers even "directories" as objects, so this method lists them too, it is your responsibility to filter them out
      */
    def objSummarySource(bucket: String, prefix: String, maxKeys: Int = 1000): Source[S3ObjectSummary, _] =
      Source.actorPublisher(Props(classOf[S3ObjectSummariesListingPublisher], new ListObjectsRequest().withBucketName(bucket).withPrefix(prefix).withMaxKeys(maxKeys), underlying))

    def commonPrefixSource(bucket: String, prefix: String, delimiter: String): Source[String, _] = {
      val slashEndingDirPath = if (prefix.endsWith(delimiter)) prefix else prefix + delimiter // commonPrefixes method expects prefix to end with delimiter
      val request = new ListObjectsRequest().withBucketName(bucket).withPrefix(slashEndingDirPath).withDelimiter(delimiter)
      Source.actorPublisher(Props(classOf[S3CommonPrefixesListingPublisher], request, underlying))
    }
  }

  trait IteratorBasedActorPublisher[T] extends ActorPublisher[T] {

    protected[this] var iterator: Iterator[T]

    protected[this] def onIteratorNext(elm: T): Unit = onNext(elm)
    protected[this] def onIteratorHasNextError(ex: Throwable): Unit = onErrorThenStop(ex) // Note that stream hasNext throws exception !
    def onIteratorCompleted(): Unit = onCompleteThenStop()

    def pushNext: Boolean = try {
      if (iterator.hasNext) {
        onIteratorNext(iterator.next())
        true
      } else {
        onIteratorCompleted()
        false
      }
    } catch {
      case NonFatal(ex) =>
        onIteratorHasNextError(ex)
        false
    }

    def receive: Receive = {
      case Request(n) if totalDemand > 0 && isActive =>
        (1L to Math.min(n, totalDemand)).foldLeft(true) {
          case (acc,i) => if (acc) pushNext else false
        }

      case Cancel =>
        context.stop(self)
    }
  }

  abstract class S3ObjectListingPublisher[T](req: ListObjectsRequest, s3: AmazonS3Client) extends IteratorBasedActorPublisher[T] with ActorLogging {
    import scala.concurrent.duration._

    private[this] def listObjects = IO.reRun(3, 10.seconds, log.error)(s3.listObjects(req))
    private[this] var objListing = listObjects

    def mapListing(objectListing: ObjectListing): Iterator[T]
    var iterator: Iterator[T] = mapListing(objListing)

    override def onIteratorCompleted(): Unit = {
      req.setMarker(objListing.getNextMarker)
      if (objListing.isTruncated) {
        objListing = listObjects
        iterator = mapListing(objListing)
        pushNext
      } else {
        objListing = null
        iterator = null
        onCompleteThenStop()
      }
    }
  }

  class S3ObjectSummariesListingPublisher(req: ListObjectsRequest, s3: AmazonS3Client) extends S3ObjectListingPublisher[S3ObjectSummary](req, s3) {
    import scala.collection.JavaConverters._
    def mapListing(objectListing: ObjectListing): Iterator[S3ObjectSummary] = objectListing.getObjectSummaries.asScala.toIterator
  }

  class S3CommonPrefixesListingPublisher(req: ListObjectsRequest, s3: AmazonS3Client) extends S3ObjectListingPublisher[String](req, s3) {
    import scala.collection.JavaConverters._
    def mapListing(objectListing: ObjectListing): Iterator[String] = objectListing.getCommonPrefixes.asScala.toIterator
  }

}