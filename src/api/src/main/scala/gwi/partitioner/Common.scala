package gwi.partitioner

import java.io._
import java.nio.charset.StandardCharsets
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object IO {
  @tailrec
  def reTry[T](attempts: Int, in: Duration, logError: (String, Throwable) => Unit)(attempt: => Try[T]): Try[T] = attempt match {
    case s@Success(_) => s
    case Failure(ex) if attempts > 1 =>
      logError("Recovering from unexpected error", ex)
      Thread.sleep(in.toMillis)
      reTry(attempts - 1, in, logError)(attempt)
    case f@Failure(ex) =>
      logError(s"Unable to recover from error, not even after trying $attempts times !!!", ex)
      f
  }

  def reRun[T](attempts: Int, in: Duration, logError: (String, Throwable) => Unit)(attempt: => T): T =
    reTry(attempts, in, logError)(Try(attempt)).get

  def streamToString(is: InputStream, bufferSizeInBytes: Int): String =
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

  def streamToSeq(is: InputStream, bufferSizeInBytes: Int): Array[String] =
    try {
      val reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), bufferSizeInBytes)
      val arr = Array.newBuilder[String]
      var line = reader.readLine()
      while (line != null) {
        arr += line
        line = reader.readLine()
      }
      arr.result()
    } finally is.close()

  def readGZipStreamAsString(is: InputStream, expectedSize: Int): String =
    IO.streamToString(new GZIPInputStream(is), expectedSize)

  def readGZipStreamLines(is: InputStream, expectedSize: Int): Array[String] =
    IO.streamToSeq(new GZIPInputStream(is), expectedSize)

  def gzipByteArray(bytes: Array[Byte]): Array[Byte] = {
    val baos = new ByteArrayOutputStream(bytes.length/10)
    val gzip = new GZIPOutputStream(baos, bytes.length/10)
    try {
      gzip.write(bytes)
      gzip.close()
      baos.toByteArray
    } finally {
      gzip.close()
    }
  }

}