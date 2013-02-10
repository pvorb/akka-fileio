package de.vorb.akka.io

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.{ AsynchronousFileChannel, CompletionHandler }

import scala.concurrent.{ ExecutionContext, Promise }

import akka.actor.IO.{ Chunk, EOF, Input, Iteratee }
import akka.util.ByteString

class FileHandle(val channel: AsynchronousFileChannel,
    val bufferSize: Int = 32 * 1024)(
        private implicit val context: ExecutionContext) {
  def size = channel.size
  def force(metaData: Boolean = true) = Promise[Unit].success(channel.force(metaData))

  def close(): Promise[Unit] = {
    try {
      channel.close()
      Promise.successful[Unit]()
    } catch {
      case e: IOException => Promise.failed(e)
    }
  }

  def write(bytes: ByteString, position: Long) = {
    val promise = Promise[Int]
    channel.write(bytes.toByteBuffer, position, null,
      new CompletionHandler[Integer, Any] {
        def completed(result: Integer, attachment: Any) {
          promise.success(result)
        }

        def failed(exc: Throwable, attachment: Any) {
          promise.failure(exc)
        }
      })

    promise
  }

  /**
   * Default implementation of the read methods to read the file.
   *
   * The implementation has to read from the given position the given amount of
   * bytes. Every chunk of data read then in passed to the accumulator by
   * calling [[de.vorb.akka.io.Accumulator#apply]]. If it return true it can
   * continue to read the data. If false is returned, it can stop reading
   * further. When reading has finished,
   * [[de.vorb.akka.io.Accumulator#finalValue]] should be called. That result
   * then is the result of the returned Future.
   *
   * The accumulator is a mutable instance, with no synchronization. The
   * implementation has to ensure the the accumulator is accessed in a
   * synchronized manner.
   *
   * @param accumulator the accumulator which builds the end result during the
   *                    read process
   * @param startPos start point of the read, from 0. If the start point is
   *                 outside the file size, a empty result is returned
   * @param amountToRead the amount to read in bytes.
   * @tparam A
   * @return
   */
  protected def readAndAccumulate[A](acc: Accumulator[A], startPos: Long = 0,
    toRead: Long = -1L): Promise[A] = {
    val bytesToRead: Long = toRead match {
      case -1L => size
      case _   => toRead
    }

    new Reader(bytesToRead, acc).start(startPos)
  }

  def read(startPos: Long, toRead: Int): Promise[ByteString] =
    readAndAccumulate(Accumulator.byteStringBuilder, startPos, toRead)

  def readAll[A](parser: Iteratee[A], startPos: Long,
    toRead: Long = -1L): Promise[A] =
    readAndAccumulate(Accumulator.parseWhole(parser), startPos, toRead)

  def readSegments[A](segmentParser: Iteratee[A], startPos: Long,
    toRead: Long = -1L): Promise[Seq[A]] =
    readAndAccumulate(Accumulator.parseSegments(segmentParser), startPos,
      toRead)

  def readChunked[A](startPos: Long, toRead: Long, initialValue: A)(
    accumulationClosure: (A, Input) => A) =
    readAndAccumulate(Accumulator.functionAccumulator(initialValue,
      accumulationClosure), startPos, toRead)

  def readChunked[A](startPos: Long, toRead: Long = -1L)(
    processingFunction: PartialFunction[Input, A]): Promise[A] = {
    readChunked[A](startPos, toRead, null.asInstanceOf[A])(
      (previousValue: A, data: Input) =>
        if (processingFunction.isDefinedAt(data))
          processingFunction(data)
        else
          null.asInstanceOf[A]
    )
  }

  /**
   * A Reader manages the results when reading from the AsynchronousFileChannel
   * and packs them into Accumulator objects.
   */
  private class Reader[A](private var toRead: Long, val acc: Accumulator[A])
      extends CompletionHandler[Integer, Reader[A]] {

    private var pos: Long = 0
    val stepSize: Int = math.min(bufferSize, toRead).toInt
    val buf: ByteBuffer = ByteBuffer.allocate(stepSize)
    val promise: Promise[A] = Promise[A]

    def start(pos: Long): Promise[A] = {
      this.pos = pos
      buf.limit(math.min(bufferSize, toRead).toInt)
      channel.read(buf, pos, this, this)
      promise
    }

    override def completed(bytesRead: Integer, reader: Reader[A]) {
      assert(Reader.this == reader)
      try {
        buf.flip()
        val bs = ByteString(buf)
        val continue = acc(Chunk(bs))
        buf.flip()
        if (bytesRead == stepSize && continue) {
          toRead -= stepSize
          pos += stepSize
          buf.limit(math.min(toRead, stepSize).toInt)
          if (toRead > 0)
            channel.read(buf, pos, this, this)
          else
            succeeded()
        } else
          succeeded()
      } catch {
        case e: IOException => promise.failure(e)
      }
    }

    private def succeeded() {
      acc(EOF)
      promise.success(acc.finalValue)
    }

    override def failed(e: Throwable, reader: Reader[A]) {
      promise.failure(e)
    }
  }
}