package de.vorb.akka.io

import java.nio.file.FileSystems
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import akka.actor.{ ActorSystem, IO }
import akka.util.ByteString

object IterateeExample extends App {
  val imprt = ByteString("import")
  val obj = ByteString("object")

  implicit val system = ActorSystem("example")
  implicit val context = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor)

  def parser = for {
    preface <- IO takeUntil obj
  } yield preface

  val path = FileSystems.getDefault
    .getPath("src/example/scala/de/vorb/akka/io/IterateeExample.scala")
  val handle = FileIO.open(path)

  val promise = handle.readAll(parser, 0)
  promise.future onSuccess {
    case bs => println(bs.utf8String)
  }
}
