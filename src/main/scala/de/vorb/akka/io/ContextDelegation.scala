package de.vorb.akka.io

import java.util.{ Collections, List }
import java.util.concurrent.{ Callable, TimeUnit }

import scala.concurrent.{ Await, ExecutionContext, ExecutionContextExecutorService, Future }
import scala.concurrent.duration.{ Duration, DurationInt }
import scala.language.postfixOps

private[io] class ContextDelegation(private val context: ExecutionContext)
    extends ExecutionContextExecutorService {

  @volatile private var closed = false

  if (context == null)
    throw new IllegalArgumentException("A execution context is required")

  def reportFailure(t: Throwable): Unit = context.reportFailure(t)
  def execute(command: Runnable): Unit = context.execute(command)
  def shutdown(): Unit = closed = true
  def shutdownNow(): List[Runnable] = {
    closed = true
    Collections.emptyList()
  }
  def isShutdown = closed
  def isTerminated = closed

  def awaitTermination(timeout: Long, unit: TimeUnit) = unsupported

  def submit[T](task: Callable[T]) = {
    val future = Future[T] {
      task.call()
    }(context)

    convertToJavaFuture(future)
  }

  def submit[T](task: Runnable, result: T) = unsupported

  def submit(task: Runnable) = {
    val future = Future[Unit] {
      task.run()
    }(context)

    convertToJavaFuture(future)
  }

  def convertToJavaFuture[T](
    future: Future[T]): java.util.concurrent.Future[T] =
    new java.util.concurrent.Future[T] {
      def cancel(mayInterruptIfRunning: Boolean) = false

      def isCancelled = false
      def isDone = future.isCompleted
      def get() = Await.result(future, 30 seconds)
      def get(timeout: Long, unit: TimeUnit) = Await.result(future,
        Duration(timeout, unit))
    }

  def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]]) = unsupported
  def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]], timeout: Long,
    unit: TimeUnit) = unsupported
  def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]]) = unsupported
  def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]], timeout: Long,
    unit: TimeUnit) = unsupported

  private def unsupported[T]: T =
    throw new UnsupportedOperationException("This operation is not allowed")
}