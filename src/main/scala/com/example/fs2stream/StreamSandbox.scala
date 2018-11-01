package com.example.fs2stream

import java.util.concurrent.Executors

import cats.Functor
import cats.effect.{IO, IOApp, Sync}
import fs2.{Chunk, Pipe, Stream, hash, text}

import scala.concurrent.ExecutionContext
import scala.{Stream => _}

object StreamSandbox {

  val blockingExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  implicit val cs = cats.effect.IO.contextShift(blockingExecutionContext)


  def main(args: Array[String]): Unit = {
    val s = Stream("a")

    val sha = s.through(text.utf8Encode).through(hash.sha1)

    val shac = sha.compile.drain

    val expected = "4e1243bd22c66e76c2ba9eddc1f91394e57f9f83".toUpperCase
    val shaString = Stream("test\n").through(text.utf8Encode).through(hash.sha1).compile.toVector.map("%02X" format _).mkString
    println(shaString)


    val a = Stream("test\n").through(text.utf8Encode).through(hash.sha1).map("%02X" format _).fold("")(_ + _)
    println("a: " + a.compile.toVector)

    val res = for {
      sha1 <- ChecksumUtils.sha1[IO](Stream("test\n"))
    } yield sha1

    assert(expected == shaString)

    println(Stream("a", "b", "c").through(text.utf8Encode).through(hash.sha1).compile.toVector)

    println(Stream[cats.Id, Byte](1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3, 5, 6).through(hash.sha1).compile.toVector)

    val s1 = Stream("1sas", "asdf", "asdf", "2", "2")
    val aa = s1.through(text.utf8Encode).through(size(new Buffer())).compile.toVector
    println("size " + aa)
  }

  class Buffer {
    var b = 0

    def update(v: Int): Unit = {
      b = b + v
    }
  }

  def size[F[_]](acc: => Buffer): Pipe[F, Byte, Int] =
    in =>
      Stream.suspend {
        in.chunks
          .fold(acc) { (d, c) =>
            d.update(c.toBytes.length)
            d
          }
          .flatMap { d =>
            Stream.chunk(Chunk.apply(d.b))
          }
      }
}

object ChecksumUtils {

  def sha1[F[_] : Sync](stream: Stream[F, String]): F[Stream[F, String]] = {
    val s = stream.through(text.utf8Encode).through(hash.sha1).map("%02X" format _).fold("")(_ + _)
    Sync[F].point(s)
  }

}
