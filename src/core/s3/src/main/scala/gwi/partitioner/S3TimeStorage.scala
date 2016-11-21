package gwi.partitioner

import java.io.{ByteArrayInputStream, File}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, StandardOpenOption}
import java.util.regex.Pattern

import com.amazonaws.services.s3.model.ObjectMetadata
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone, Interval}

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Try

case class S3Source(bucket: String, path: String, access: String, properties: Map[String,String]) extends StorageSource {
  require(path.endsWith("/"), s"By convention, s3 paths must end with delimiter otherwise the key doesn't represent a directory, $path is invalid !!!")
}
case class S3TimeStorage(id: String, source: S3Source, partitioner: S3TimePartitioner) extends TimeStorage[S3Source, S3TimePartitioner, S3TimeClient]

object S3TimeStorage {

  implicit class S3TimeStoragePimp(underlying: S3TimeStorage) {

    def client(implicit driver: S3Driver) = new S3TimeClient {
      private val S3TimeStorage(_, source, partitioner) = underlying
      private val successFile = {
        val tmpFile = new File(sys.props("java.io.tmpdir") + "/.success")
        tmpFile.delete()
        val version = s"${BuildInfo.name}-${BuildInfo.version}"
        Files.write(tmpFile.toPath, version.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE)
        tmpFile
      }

      private def checkPermissions() = require(source.access.contains("w"), s"s3://${source.bucket}/${source.path} has not write permissions !!!")

      def lookup(p: TimePartition): S3Pointer = underlying.partitioner.construct(p, source)

      def delete(partition: TimePartition): Unit = {
        checkPermissions()
        val pointer = lookup(partition)
        driver
          .listKeys(pointer.bucket, pointer.partitionKey)
          .foreach(driver.deleteObject(source.bucket, _))
      }

      def indexData(partition: TimePartition, fileName: String, content: String) = {
        checkPermissions()
        val inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
        val metaData = new ObjectMetadata()
        metaData.setContentLength(content.length)
        driver.putObject(source.bucket, lookup(partition).partitionFileKey(fileName), inputStream, metaData)
      }

      def markWithSuccess(partition: TimePartition): Unit = {
        driver.putObject(source.bucket, lookup(partition).partitionFileKey(successFile.getName), successFile)
      }

      def list: Future[Seq[TimePartition]] =
        list(new Interval(new DateTime(2015, 1, 1, 0, 0, 0, DateTimeZone.UTC), partitioner.granularity.truncate(new DateTime(DateTimeZone.UTC))))

      def list(range: Interval): Future[Seq[TimePartition]] = {
        def timePath(time: DateTime) = partitioner.dateToPath(time).split("/").filter(_.nonEmpty)
        val commonAncestorList =
          timePath(range.getStart).zip(timePath(range.getEnd))
            .takeWhile(p => p._1 == p._2)
            .map(_._1)

        val storagePrefix = s"${source.path}${commonAncestorList.mkString("/")}"
        val pathDepth = partitioner.granularity.arity - commonAncestorList.length
        def isValidPartition(timePath: S3Pointer) = driver.doesObjectExist(source.bucket, timePath.partitionFileKey(".success"))

        driver.getRelativeDirPaths(source.bucket, storagePrefix, pathDepth, "/", math.pow(3, partitioner.granularity.arity).toInt)
          .map { s3DirPaths =>
            s3DirPaths
              .map(commonAncestorList ++ _)
              .map(arr => S3Pointer(source.bucket, source.path, arr.mkString("", "/", "/")))
              .map(path => path -> partitioner.deconstruct(path).get)
              .collect { case (path, partition) if range.contains(partition.value.getStart) && isValidPartition(path) => partition }
              .sortWith { case (x, y) => x.value.getStart.compareTo(y.value.getStart) > 1 }
              .toVector
          }(ExeC.global)
      }
    }
  }
}

trait S3TimeClient extends TimeClient {
  type OUT = S3Pointer
  def indexData(partition: TimePartition, fileName: String, content: String): Unit
}

case class S3Pointer(bucket: String, path: String, timePath: String) extends Pointer {
  def partitionKey: String = path + timePath
  def partitionFileKey(name: String): String = path + timePath + name
}

case class S3TimePartitioner(granularity: Granularity, private val pathFormat: Option[String], private val pathPattern: Option[String]) extends TimePartitioner {
  type OUT = S3Pointer
  type S = S3Source
  private[this] val pathFormatter = DateTimeFormat.forPattern(pathFormat.getOrElse(S3TimePartitioner.PlainPathFormat).split("/").take(granularity.arity).mkString("/"))
  private[this] val compiledPathPattern = Pattern.compile(pathPattern.getOrElse(S3TimePartitioner.PlainPathPattern))

  def dateToPath(dateTime: DateTime): String = pathFormatter.print(dateTime) + "/"

  def construct(p: TimePartition, s: S3Source): S3Pointer = S3Pointer(s.bucket, s.path, dateToPath(p.value.getStart))

  def deconstruct(path: S3Pointer): Option[TimePartition] = {
    val matcher = compiledPathPattern.matcher(path.timePath)
    def group(i: Int): Option[Int] = Try(matcher.group(i)).map(Option(_).map(_.toInt)).getOrElse(None)
    Option(matcher.matches())
      .filter(identity)
      .map ( _ => Array(group(1),group(2),group(3),group(4),group(5),group(6)) )
      .collect { case dateVals if dateVals.takeWhile(_.isDefined).length >= granularity.arity =>
        val dv = (0 to 5).map( i => if (i < granularity.arity) dateVals(i).get else 0 )
        buildPartition(granularity.bucket(new DateTime(dv(0), dv(1), dv(2), dv(3), dv(4), dv(5), DateTimeZone.UTC)))
      }
  }

  def getPathFormat: String = pathFormat.getOrElse(S3TimePartitioner.PlainPathFormat).split("/").take(granularity.arity).mkString("/")
}

object S3TimePartitioner {
  val PlainPathFormat = "yyyy/MM/dd/HH/mm/ss"
  val QualifiedPathFormat = "'y'=yyyy/'m'=MM/'d'=dd/'H'=HH/'M'=mm/'S'=ss"
  val PlainPathPattern = "^.*(\\d{4})/(?:(\\d{2})/(?:(\\d{2})/(?:(\\d{2})/(?:(\\d{2})/(?:(\\d{2})/)?)?)?)?)?.*$"
  val QualifiedPathPattern = "^.*[Yy]=(\\d{4})/(?:[Mm]=(\\d{2})/(?:[Dd]=(\\d{2})/(?:[Hh]=(\\d{2})/(?:[Mm]=(\\d{2})/(?:[Ss]=(\\d{2})/)?)?)?)?)?.*$"

  def plain(g: Granularity): S3TimePartitioner = S3TimePartitioner(g, Some(PlainPathFormat), Some(PlainPathPattern))
  def qualified(g: Granularity): S3TimePartitioner = S3TimePartitioner(g, Some(QualifiedPathFormat), Some(QualifiedPathPattern))
}