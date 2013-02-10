package de.vorb.akka.io

import scala.collection.mutable.Buffer

import akka.actor.IO._
import akka.util.ByteString

trait Accumulator[A] {
  def apply(input: Input): Boolean
  def finalValue: A
}

object Accumulator {
  def parseWhole[A](parser: Iteratee[A]) = new Accumulator[A] {
    private val it = IterateeRef.sync(parser)

    def apply(input: Input): Boolean = {
      it(input)
      it.value._1 match {
        case Done(_) => false
        case _       => true
      }
    }

    def finalValue: A = it.value._1.get
  }

  def byteStringBuilder = new Accumulator[ByteString] {
    val builder = ByteString.newBuilder

    def apply(input: Input): Boolean = {
      input match {
        case Chunk(bytes) =>
          builder ++= bytes
        case _ =>
      }

      true
    }

    def finalValue = builder.result
  }

  def functionAccumulator[A](initialLastValue: A, fn: (A, Input) => A) =
    new Accumulator[A] {
      private var lastValue = initialLastValue

      def apply(input: Input): Boolean = {
        lastValue = fn(lastValue, input)
        true
      }

      def finalValue = lastValue
    }

  def parseSegments[A](parser: Iteratee[A]) = new Accumulator[Seq[A]] {
    val buffer: Buffer[A] = Buffer[A]()
    var it: Iteratee[A] = parser

    def apply(input: Input): Boolean = {
      if (input == EOF) {
        buffer += it(input)._1.get
      } else {
        var (parsedValue, rest) = it(input)
        while (parsedValue.isInstanceOf[Done[A]]) {
          buffer += parsedValue.get
          val (newParsedValue, newRest) = parser(rest)
          parsedValue = newParsedValue
          rest = newRest
        }
        it = parsedValue
      }

      true
    }

    def finalValue: Seq[A] = buffer.toSeq
  }
}