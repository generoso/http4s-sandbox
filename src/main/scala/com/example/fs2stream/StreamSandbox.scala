package com.example.fs2stream

import java.security.MessageDigest
import java.util.concurrent.Executors

import cats.Id
import cats.effect.{IO, Sync}
import fs2.{Chunk, Pipe, Stream, hash, text}

import scala.concurrent.ExecutionContext
import scala.{Stream => _}

object StreamSandbox {

  val blockingExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  implicit val cs = cats.effect.IO.contextShift(blockingExecutionContext)

  def main(args: Array[String]): Unit = {

    sha1trials()

    composition()

  }

  private def sha1trials(): Unit = {
    val s = Stream("a")
    val sha = s.through(text.utf8Encode).through(hash.sha1)
    val shac = sha.compile.drain

    val expected = "4e1243bd22c66e76c2ba9eddc1f91394e57f9f83".toUpperCase
    val shaString = Stream("test\n").through(text.utf8Encode).through(hash.sha1).compile.toVector.map("%02X" format _).mkString
    assert(expected == shaString)
    println("shaString  : " + shaString)
    val shaString1 = Stream("test\n").through(text.utf8Encode).through(hash.sha1).map("%02X" format _).fold("")(_ + _)
    println("shaString1 : " + shaString1.compile.toVector.head)

    val res = for {
      sha1 <- ChecksumUtils.sha1[IO](Stream("test\n"))
    } yield sha1
    println("shaString2 : " + res.unsafeRunSync().compile.toVector.unsafeRunSync().head)

    println(Stream("a", "b", "c").through(text.utf8Encode).through(hash.sha1).compile.toVector)

    println(Stream[Id, Byte](1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3, 5, 6).through(hash.sha1).compile.toVector)
  }

  private def composition(): Unit = {
    val s1 = Stream("1sas", "asdf", "asdf", "2", "2")

    // compute size and sha separately
    val ss = s1.through(text.utf8Encode).through(size(new IntBuffer())).compile.toVector
    println("size " + ss)
    val sha1 = s1.through(text.utf8Encode).through(hash.sha1).compile.toVector.map("%02X" format _).mkString
    println("sha1: " + sha1)

    // 1st method
    val compPipe = composePipes[Id, Byte](sizep, hash.sha1)
    val resStream1 = s1.through(text.utf8Encode).through(compPipe).compile.toVector
    println("composed 1 " + myFold(resStream1))

    // 2dn method
    val resStream2 = s1.through(text.utf8Encode).through(sizeAndSha1).compile.toVector
    println("composed 2 " + resStream2.head)
  }

  // stream executed twice ?
  def composePipes[F[_], A](sizep: Pipe[F, A, Int], sha1: Pipe[F, A, Byte]): Pipe[F, A, (Int, Byte)] = {
    s: Stream[F, A] => {
      val s1 = s.through(sizep)
      val s2 = s.through(sha1)
      val s3 = s1.zipAll(s2) _
      s3(0, '.')
    }
  }

  private def myFold(bb: Vector[(Int, Byte)]) = {
    bb
      .map { case (c, d) => (c, "%02X" format d) }
      .foldLeft((0, "")) { case ((count, digest), (i, v)) => (count + i, digest + v) }
  }

  class IntBuffer {
    var value = 0

    def update(v: Int): Unit = {
      value = value + v
    }
  }

  def sizep[F[_]]: Pipe[F, Byte, Int] = size(new IntBuffer)

  def size[F[_]](acc: => IntBuffer): Pipe[F, Byte, Int] =
    in =>
      Stream.suspend {
        in.chunks
          .fold(acc) { (d, c) =>
            d.update(c.toBytes.length)
            d
          }
          .flatMap { d =>
            Stream.chunk(Chunk.apply(d.value))
          }
      }

  def sizeAndSha1[F[_]]: Pipe[F, Byte, (Int, String)] = sizeAndDigest(new IntBuffer, MessageDigest.getInstance("SHA-1"))

  def sizeAndDigest[F[_]](size: => IntBuffer, digest: => MessageDigest): Pipe[F, Byte, (Int, String)] = {
    in =>
      Stream.suspend {
        in.chunks
          .fold((size, digest)) { case ((s, d), c) =>
            val bytes = c.toBytes
            s.update(bytes.length)
            d.update(bytes.values, bytes.offset, bytes.size)
            (s, d)
          }
          .flatMap { case (s, d) =>
            Stream.chunk(Chunk.apply((s.value, d.digest().map("%02X" format _).mkString)))
          }
      }
  }

}

object ChecksumUtils {

  def sha1[F[_] : Sync](stream: Stream[F, String]): F[Stream[F, String]] = {
    val s = stream.through(text.utf8Encode).through(hash.sha1).map("%02X" format _).fold("")(_ + _)
    Sync[F].point(s)
  }

}
