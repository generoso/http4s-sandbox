package com.example.spark

import java.io.File
import java.nio.file.Paths
import java.util.concurrent.Executors

import cats.implicits._
import cats.Functor
import cats.effect.{IO, Sync}
import fs2.{Stream, hash, text}
import org.apache.commons.io.FileUtils
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import scala.concurrent.ExecutionContext
import scala.{Stream => _}

// generate random file with 100 chars lines (sorted)
// tr -dc A-Za-z0-9 </dev/urandom | head -c 10M  | sed -e "s/.\{100\}/&\n/g" | sort  > /tmp/10M

object SimpleApp {

  val blockingExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  implicit val cs = cats.effect.IO.contextShift(blockingExecutionContext)
  val streamPath = "/tmp/stream"
  val rddPath = "/tmp/rdd"
  val inputFile = "/tmp/300M"

  def main(args: Array[String]): Unit = {

    cleanFiles

    val spark = SparkSession.builder.master("local").appName("SimpleApp").getOrCreate()
    val ds = spark.read.textFile(inputFile)
    val rdd = ds.rdd.sortBy(identity).cache()

    println(" ### partitions: " + rdd.getNumPartitions)
    time(rdd.saveAsTextFile(rddPath), "save as text")

    val stream = toStream(rdd.toLocalIterator).map(_ + "\n").through(text.utf8Encode)
    val w = stream.through(fs2.io.file.writeAll[IO](Paths.get(streamPath), blockingExecutionContext)).compile.drain
    val r = fs2.io.file.readAll[IO](Paths.get(streamPath), blockingExecutionContext, 4096)

    //time(w.unsafeRunSync(), "write stream")

    //time(r.compile.toList.unsafeRunSync(), "read file")

    val program = for {
      sha <- sha1(stream)
      _ <- IO(println(" ### " + sha))
    } yield ()
    time(program.unsafeRunSync, "sha")

    cleanFiles

    spark.stop()

  }

  private def cleanFiles = {
    FileUtils.deleteQuietly(new File(streamPath))
    FileUtils.deleteQuietly(new File(rddPath))
  }

  private def toStream[A](iterator: Iterator[A]) =
    Stream.unfold[IO, Iterator[A], A](iterator)(i => if (i.hasNext) Some((i.next, i)) else None)

  private def partition(rdd: RDD[String]) = {
    rdd.mapPartitions(it => {
      Iterator(it.foldLeft("")((acc, s) => acc + s))
    })
  }

  def sha1[F[_] : Sync : Functor](stream: Stream[F, Byte]): F[String] = {
    val f = stream.through(hash.sha1).map("%02X" format _).fold("")(_ + _).compile.toVector
    Functor[F].map(f)(_.head)
  }

  def time[R](block: => R, title: String): R = {
    val t0 = System.nanoTime()
    val result = block
    val t1 = System.nanoTime()
    val delta = (t1 - t0) / 1000000000.0
    println(s" ### [$title] Elapsed time: $delta s")
    result
  }

  private def rddPrint(rdd: RDD[String]): Unit = rdd.collect.foreach(println)

}
