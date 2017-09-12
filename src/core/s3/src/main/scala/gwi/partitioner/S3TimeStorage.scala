package gwi.partitioner

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

import akka.Done
import akka.stream.scaladsl.{Sink, Source}
import com.amazonaws.services.s3.model.ObjectMetadata
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone, Interval}

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.Future
import scala.util.Try

case class S3Source(bucket: String, path: String, access: String, meta: Set[String], properties: Map[String,String]) extends StorageSource {
  require(path.endsWith("/"), s"By convention, s3 paths must end with delimiter otherwise the key doesn't represent a directory, $path is invalid !!!")
}

case class S3TimeStorage(id: String, source: S3Source, partitioner: S3TimePartitioner) extends TimeStorage[S3Source, S3TimePartitioner, S3TimeClient] {
  def liftMany(i: Interval): Iterable[S3TimePartition] = partitioner.buildMany(i).map(lift)
  def lift(p: TimePartition): S3TimePartition = lift(p.value)
  def lift(start: DateTime): S3TimePartition = S3TimePartition(source.bucket, source.path, partitioner.dateToPath(start), partitioner.granularity.bucket(start))
  def lift(i: Interval): S3TimePartition = lift(i.getStart)
}

object S3TimeStorage {
  val SuccessFileName = ".success"

  implicit class S3TimeStoragePimp(underlying: S3TimeStorage) {

    def client(implicit driver: S3Driver) = new S3TimeClient {
      private val S3TimeStorage(_, source, partitioner) = underlying

      private val permissionError = s"s3://${source.bucket}/${source.path} has not write permissions !!!"
      private def hasPermissions = source.access.contains("w")

      def delete(partition: TimePartition): Future[Done] =
        if (!hasPermissions)
          Future.failed(new IllegalArgumentException(permissionError))
        else
        Source.fromIterator( () => driver.listKeys(source.bucket, underlying.lift(partition.value).partitionKey).iterator )
          .mapAsync(16) ( key => Future(driver.deleteObject(source.bucket, key))(S3Driver.cachedScheduler) )
          .runWith(Sink.ignore)(driver.mat)

      def indexData(partition: TimePartition, fileName: String, content: String): Unit = {
        require(hasPermissions, permissionError)
        val inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
        val metaData = new ObjectMetadata()
        metaData.setContentLength(content.length)
        driver.putObject(source.bucket, underlying.lift(partition.value).partitionFileKey(fileName), inputStream, metaData)
      }

      def markWithSuccess(partition: TimePartition): Future[Done] =
        Future(driver.putObject(source.bucket, underlying.lift(partition.value).partitionFileKey(SuccessFileName), source.meta.mkString("","\n","\n")))(Implicits.global)
          .map(_ => Done)(Implicits.global)

      def listAll: Future[Seq[TimePartition]] =
        list(new Interval(new DateTime(2015, 1, 1, 0, 0, 0, DateTimeZone.UTC), partitioner.granularity.truncate(new DateTime(DateTimeZone.UTC))))

      def list(range: Interval): Future[Seq[TimePartition]] = {
        def timePath(time: DateTime) = partitioner.dateToPath(time).split("/").filter(_.nonEmpty)
        val commonAncestorList =
          timePath(range.getStart).zip(timePath(range.getEnd))
            .takeWhile(p => p._1 == p._2)
            .map(_._1)

        val storagePrefix = s"${source.path}${commonAncestorList.mkString("/")}"
        val pathDepth = partitioner.granularity.arity - commonAncestorList.length
        def isValidPartition(timePath: TimePartition) = driver.doesObjectExist(source.bucket, underlying.lift(timePath).partitionFileKey(SuccessFileName))

        Source.fromFuture(driver.getRelativeDirPaths(source.bucket, storagePrefix, pathDepth, "/"))
          .mapConcat(_.toVector)
          .map(commonAncestorList ++ _)
          .map(arr => underlying.partitioner.build(underlying.partitioner.pathToInterval(arr.mkString("", "/", "/"))))
          .filter( path => range.contains(path.value) )
          .mapAsync(64)( path => Future(path -> isValidPartition(path))(S3Driver.cachedScheduler) )
          .collect { case (path,isValid) if isValid => path }
          .runWith(Sink.seq)(driver.mat)
          .map(_.sortWith { case (x, y) => x.value.getStart.compareTo(y.value.getStart) < 0 })(Implicits.global)
      }
    }
  }
}

trait S3TimeClient extends TimeClient {
  def indexData(partition: TimePartition, fileName: String, content: String): Unit
}

case class S3TimePartition(bucket: String, path: String, timePath: String, value: Interval) extends StoragePartition[Interval] {
  def partitionKey: String = path + timePath
  def partitionFileKey(name: String): String = path + timePath + name
}

case class S3TimePartitioner(granularity: Granularity, private val pathFormat: Option[String], private val pathPattern: Option[String]) extends TimePartitioner with TimePartitionBuilders {
  private[this] val pathFormatter = DateTimeFormat.forPattern(pathFormat.getOrElse(S3TimePartitioner.PlainPathFormat).split("/").take(granularity.arity).mkString("/"))
  private[this] val compiledPathPattern = Pattern.compile(pathPattern.getOrElse(S3TimePartitioner.PlainPathPattern))

  def dateToPath(dateTime: DateTime): String = pathFormatter.print(dateTime) + "/"
  def getPathFormat: String = pathFormat.getOrElse(S3TimePartitioner.PlainPathFormat).split("/").take(granularity.arity).mkString("/")
  def pathToInterval(timePath: String): Interval = {
    val matcher = compiledPathPattern.matcher(timePath)
    def group(i: Int): Option[Int] = Try(matcher.group(i)).map(Option(_).map(_.toInt)).getOrElse(None)
    Option(matcher.matches())
      .filter(identity)
      .map(_ => Array(group(1), group(2), group(3), group(4), group(5), group(6)))
      .collect { case dateVals if dateVals.takeWhile(_.isDefined).length >= granularity.arity =>
        val dv = (0 to 5).map(i => if (i < granularity.arity) dateVals(i).get else 0)
        granularity.bucket(new DateTime(dv(0), dv(1), dv(2), dv(3), dv(4), dv(5), DateTimeZone.UTC))
      }.getOrElse(throw new IllegalArgumentException(s"TimePath $timePath is not valid !!!"))
  }

}

object S3TimePartitioner {
  val PlainPathFormat = "yyyy/MM/dd/HH/mm/ss"
  val QualifiedPathFormat = "'y'=yyyy/'m'=MM/'d'=dd/'H'=HH/'M'=mm/'S'=ss"
  val PlainPathPattern = "^.*(\\d{4})/(?:(\\d{2})/(?:(\\d{2})/(?:(\\d{2})/(?:(\\d{2})/(?:(\\d{2})/)?)?)?)?)?.*$"
  val QualifiedPathPattern = "^.*[Yy]=(\\d{4})/(?:[Mm]=(\\d{2})/(?:[Dd]=(\\d{2})/(?:[Hh]=(\\d{2})/(?:[Mm]=(\\d{2})/(?:[Ss]=(\\d{2})/)?)?)?)?)?.*$"

  def plain(g: Granularity): S3TimePartitioner = S3TimePartitioner(g, Some(PlainPathFormat), Some(PlainPathPattern))
  def qualified(g: Granularity): S3TimePartitioner = S3TimePartitioner(g, Some(QualifiedPathFormat), Some(QualifiedPathPattern))
}