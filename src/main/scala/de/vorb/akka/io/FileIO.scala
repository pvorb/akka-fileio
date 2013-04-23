package de.vorb.akka.io

import java.io.EOFException
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.{ OpenOption, Path, StandardOpenOption }
import scala.collection.JavaConversions
import scala.concurrent.{ ExecutionContext, Future }
import akka.actor.IO
import akka.actor.IO.{ Chunk, Done, EOF, Error, Failure, Input, Iteratee, Next }
import akka.util.ByteString
import java.util.regex.Pattern

trait FileIO {
  def size: Long
  def force(metaData: Boolean = true): Unit
  def read(startPoint: Long, amount: Int): Future[ByteString]
}

object FileIO {
  private val defaultOpenOptions: Set[OpenOption] = Set(StandardOpenOption.READ)

  def open(file: Path, openOptions: Set[OpenOption] = defaultOpenOptions)(
    implicit context: ExecutionContext) = {
    val channel = AsynchronousFileChannel.open(file,
      JavaConversions.setAsJavaSet(openOptions), new ContextDelegation(context))
    new FileHandle(channel)
  }

  def takeUntil(delimiters: Seq[ByteString],
    inclusive: Boolean = false): Iteratee[ByteString] = {

    def step(taken: ByteString)(input: Input): (Iteratee[ByteString], Input) =
      input match {
        case Chunk(more) =>
          val bytes = taken ++ more

          for (delimiter <- delimiters) {
            val startIdx = bytes.indexOfSlice(delimiter,
              math.max(taken.length - delimiter.length, 0))
            if (startIdx >= 0) {
              val endIdx = startIdx + delimiter.length
              return (Done(bytes take (if (inclusive) endIdx else startIdx)),
                Chunk(bytes drop endIdx))
            }
          }
          (Next(step(bytes)), Chunk.empty)
        case EOF =>
          (Failure(new EOFException("Unexpected EOF")), EOF)
        case e @ Error(cause) =>
          (Failure(cause), e)
      }

    Next(step(ByteString.empty))
  }

  def takeListUntil(delimiter: ByteString, inclusive: Boolean = false)(
    iter: Iteratee[ByteString]): Iteratee[List[ByteString]] = {
    def step(taken: ByteString, list: List[ByteString])(input: Input): (Iteratee[List[ByteString]], Input) = {
      input match {
        case Chunk(more) =>
          val bytes = taken ++ more

          val startIdx = bytes.indexOfSlice(delimiter,
            math.max(taken.length - delimiter.length, 0))
          if (startIdx >= 0) {
            val endIdx = startIdx + delimiter.length
            return (Done(list), input)
          }
      }
      null
    }

    Next(step(ByteString.empty, Nil))
  }
}
