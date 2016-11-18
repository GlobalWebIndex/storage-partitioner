package gwi.partitioner

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

object ExeC {
  object sameThread extends ExecutionContext {
    override def execute(runnable: Runnable): Unit = runnable.run()
    override def reportFailure(t: Throwable): Unit = { println(t.getMessage); t.printStackTrace() }
  }
  def fixed(size: Int) = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(size))
  def global = ExecutionContext.global
}
