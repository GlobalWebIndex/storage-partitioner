package gwi.partitioner

import java.io.{FileInputStream, InputStream}
import java.nio.file.{Files, Paths}

import org.scalameter.api._
import org.scalameter.{Bench, Executor, Gen, KeyValue}

object IOBench extends Bench.OnlineRegressionReport {

  override def measurer = new Executor.Measurer.Default

  private val testFilePath = "target/IOBench.txt"

  private def writeFile(lines: Int) = {
    val testLine = "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book."
    Files.write(Paths.get(testFilePath), IO.gzipByteArray((0 to lines).map(_ => testLine).mkString("", "\n", "\n").getBytes))
  }

  private def deleteFile() = Files.delete(Paths.get(testFilePath))

  private val execs = Seq[KeyValue](
    exec.benchRuns -> 3,
    exec.maxWarmupRuns -> 2,
    exec.independentSamples -> 2,
    exec.requireGC -> false,
    exec.jvmflags -> List("-server", "-Xms128m", "-Xmx256m", "-XX:+UseG1GC")
  )

  private def bench(name: String, gen: Gen[Int])(fn: (InputStream, Int) => Unit) = {
    performance of "stream" in {
      performance of name in {
        using(gen)
          .config(execs: _*)
          .setUp(_ => writeFile(10000))
          .tearDown(_ => deleteFile())
          .in(bufferSize => fn(new FileInputStream(testFilePath), bufferSize))
      }
    }
  }

  bench("Seq", Gen.range("size")(8192, 262144, 8192))( (is, bufferSize) => IO.readGZipStreamLines(is, bufferSize))
  bench("String", Gen.range("size")(8192, 262144, 8192))((is, bufferSize) => IO.readGZipStreamAsString(is, bufferSize).split("\n"))

}