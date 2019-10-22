package net.degoes.zio

import java.io.IOException

import zio.{ App, Task, UIO, ZIO }
import zio.console._
import zio.random._

import scala.util.Try

object App4 extends App with Helpers {

  def parseInt(s: String): Option[Int] = Try(s.toInt).toOption

  // version that converts our option into a ZIO effect
  def parseIntM(s: String): ZIO[Any, Unit, Int] = ZIO.fromOption(parseInt(s))

  def eventually[R, E, A](zio: ZIO[R, E, A]): ZIO[R, Nothing, A] =
    zio orElse (eventually(zio))

  def retry[R, E, A](n: Int, zio: ZIO[R, E, A]): ZIO[R, E, A] =
    if (n <= 1) zio else zio orElse (retry(n - 1, zio))

  val getIntegerGuess: ZIO[Console, Any, Int] =
    getStrLn.flatMap(s => parseIntM(s).tapError(_ => putStrLn("that wasn't a number!!")))

  def gameLoop(rand: Int): ZIO[Console, Any, Unit] =
    for {
      _ <- putStrLn("guess a number 1-10")
      // tapError does a map on the left side
      guess <- getIntegerGuess.eventually // use ZIO provided version of our eventually above
      loop <- if (guess == rand) putStrLn("well done") as false
             else putStrLn("guess again") as true
      _ <- if (loop) gameLoop(rand) else UIO()
    } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
    (for {
      _    <- putStrLn("The number guessing game!")
      ran  <- nextInt(10)
      loop <- gameLoop(ran)
    } yield ()).exited
}
