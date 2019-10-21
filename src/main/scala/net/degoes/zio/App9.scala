package net.degoes.zio

import java.io.{File, FileInputStream, IOException, InputStream}

import zio._
import zio.blocking.{Blocking, effectBlocking}
import zio.console.{Console, putStrLn, _}

import scala.util.Try


object App9 extends App with Helpers {

  def fib(n:Int): UIO[BigInt] = {
    if (n <= 1) UIO(n)
    else fib(n - 2).zipWith(fib(n - 1))(_ + _)
  }

  def getNumber = getStrLn.flatMap(s => Task(s.toInt)).tapError(_ => putStrLn("You need to enter a number")).eventually

  override def run(args: List[String]): ZIO[Console with Blocking, Nothing, Int] = {
    (for {
      _ <- putStrLn("How many terms would you like to compute in parallel?")
      n <- getNumber
      nums <- ZIO.foreach(0 until n) { i =>
        for {
          _ <- putStrLn(s"Please enter a fib number ${i + 1} to compute")
          n <- getNumber
        } yield ( n )
      }
      // ParN(num) limits the number of parallel fibres created. Usually you want to use ParN rather than Par
      // There are lots of standard Par variants of existing functions like zip
      fibs <- ZIO.foreachParN(6)(nums.zipWithIndex) {
        case (num, i) => {
          for {
            fibNum <- fib(num)
            _ <- putStrLn(s"Term ${i + 1} is ${fibNum}")
          } yield (fibNum)
        }
      }
      _ <- putStrLn(s"The numbers in order of entry are ${fibs}")
//      _ <- fibers.zipWithIndex.foldLeft[ZIO[Console, Nothing, Unit]](UIO.unit) {
//        case (acc, (fiber, index)) =>
//          acc *> (for {
//            value <- fiber.join   // waits for fiber to complete. doesn't block anything else
//            _ <- putStrLn(s"The ${index + 1} term is ${value}")
//          } yield ())
//      }

    } yield ()).exited
  }
}
