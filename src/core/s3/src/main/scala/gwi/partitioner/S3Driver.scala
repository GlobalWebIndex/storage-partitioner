package gwi.partitioner

import java.io.{BufferedReader, InputStream, InputStreamReader, StringWriter}
import java.nio.charset.StandardCharsets
import java.util
import java.util.zip.GZIPInputStream

import com.amazonaws.auth.{AWSCredentials, BasicAWSCredentials}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{ListObjectsRequest, ObjectListing, S3ObjectInputStream}
import com.amazonaws.{ClientConfiguration, ClientConfigurationFactory}

import scala.annotation.tailrec
import scala.collection.mutable.Builder
import scala.concurrent.Future
import scala.util.Try

class S3Driver(credentials: AWSCredentials, region: Region, config: ClientConfiguration) extends AmazonS3Client(credentials, config) {
  setRegion(region)
}

object S3Driver {
  def apply(id: String, key: String, region: String, config: ClientConfiguration = new ClientConfigurationFactory().getConfig): S3Driver =
    new S3Driver(new BasicAWSCredentials(id, key), Region.getRegion(Regions.fromName(region)), config)

  implicit class Pimp(s3: AmazonS3Client) {

    private def streamToString(is: InputStream, bufferSizeInBytes: Int): String =
      try {
        val reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), bufferSizeInBytes)
        val writer = new StringWriter(bufferSizeInBytes/2)
        val buffer = new Array[Char](bufferSizeInBytes/2)
        var length = reader.read(buffer)
        while (length > 0) {
          writer.write(buffer, 0, length)
          length = reader.read(buffer)
        }
        writer.toString
      } finally is.close()

    def readObjectStream[T](bucketName: String, key: String)(fn: (S3ObjectInputStream) => T): T = {
      val s3Obj = s3.getObject(bucketName, key)
      try fn(s3Obj.getObjectContent) finally Try(s3Obj.close())
    }

    def readObjectStreamAsString(bucketName: String, key: String, expectedSize: Int): String =
      readObjectStream(bucketName, key)( is => streamToString(is, expectedSize))

    def readObjectGZipStreamAsString(bucketName: String, key: String, expectedSize: Int): String =
      readObjectStream(bucketName, key)( is => streamToString(new GZIPInputStream(is, expectedSize/8), expectedSize))

    def listKeys(bucketName: String, prefix: String): Vector[String] = {
      import scala.collection.JavaConverters._
      @tailrec
      def recursively(listing: ObjectListing, summaries: Builder[String, Vector[String]]): Builder[String, Vector[String]] = {
        summaries ++= listing.getObjectSummaries.asScala.map(_.getKey)
        if (listing.isTruncated) {
          recursively(s3.listNextBatchOfObjects(listing), summaries)
        } else {
          summaries
        }
      }
      recursively(s3.listObjects(bucketName, prefix), Vector.newBuilder[String]).result()
    }

    def commonPrefixSource(bucket: String, prefix: String, delimiter: String, expectedSize: Int): Seq[String] = {
      @tailrec
      def recursively(req: ListObjectsRequest, listing: ObjectListing, result: util.List[String]): util.List[String] = {
        result.addAll(listing.getCommonPrefixes)
        if (listing.isTruncated) {
          req.setMarker(listing.getNextMarker)
          recursively(req, s3.listObjects(req), result)
        } else {
          result
        }
      }
      import scala.collection.JavaConverters._
      val slashEndingDirPath = if (prefix.endsWith(delimiter)) prefix else prefix + delimiter // commonPrefixes method expects prefix to end with delimiter
      val req = new ListObjectsRequest().withBucketName(bucket).withPrefix(slashEndingDirPath).withDelimiter(delimiter)
      recursively(req, s3.listObjects(req), new util.ArrayList[String](expectedSize)).asScala
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
    def getRelativeDirPaths(bucket: String, prefix: String, atLevel: Int, delimiter: String, parallelism: Int): Future[Seq[Seq[String]]] = {
      implicit val executionContext = ExeC.fixed(parallelism)
      def listPrefixes(pref: String) = Future(commonPrefixSource(bucket, pref, delimiter, 100))

      def recursively(prefixesF: Future[Seq[String]]): Future[Seq[String]] = {
        prefixesF.flatMap {
          case prefixes if prefixes.isEmpty =>
            Future.successful(Seq.empty)
          case prefixes =>
            recursively(Future.sequence(prefixes.map(listPrefixes)).map(_.flatten)(ExeC.sameThread)).map(prefixes ++ _)
        }(ExeC.sameThread)
      }
      recursively(listPrefixes(prefix)).map { dirNames =>
        dirNames
          .map(_.substring(prefix.length).split(delimiter).filter(_.nonEmpty))
          .collect {
            case arr if arr.length == atLevel => arr.toSeq
          }
      }
    }

  }

}