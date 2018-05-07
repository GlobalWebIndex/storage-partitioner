package gwi.partitioner

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.UncaughtExceptionReporter
import monix.execution.schedulers.AsyncScheduler

import scala.concurrent.ExecutionContext

object CachedScheduler {

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

  lazy val instance =
    AsyncScheduler(
      Executors.newSingleThreadScheduledExecutor(),
      ExecutionContext.fromExecutorService(friendlyCachedThreadPoolExecutor("cached-scheduler", 4, 20, 60)),
      UncaughtExceptionReporter.LogExceptionsToStandardErr,
      AlwaysAsyncExecution
    )
}