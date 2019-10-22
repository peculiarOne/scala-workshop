package net.degoes.zio

import java.io.IOException

import zio._
import zio.blocking.Blocking
import zio.console.{ Console, putStrLn, _ }

import scala.util.Try

object App10 extends App with Helpers {
  sealed trait Command

  object Command {
    final case class ComputeFib(n: Int) extends Command
    case object Quit                    extends Command

    @scala.annotation.tailrec
    def fromString(s: String): Option[Command] =
      s.trim.toLowerCase() match {
        case "quit" | "exit"                => Some(Quit)
        case fib if (fib.startsWith("fib")) => fromString(fib.drop(3).trim())
        case value                          => Try(value.toInt).toOption.map(ComputeFib)
      }
  }

  final def fib(n: Int): UIO[BigInt] =
    if (n <= 1) UIO(n)
    else fib(n - 2).zipWith(fib(n - 1))(_ + _)

  def getNumber: ZIO[Console, Nothing, Int] =
    getStrLn.flatMap(s => Task(s.toInt)).tapError(_ => putStrLn("You need to enter a number")).eventually

  val promptCommand: ZIO[ZEnv, IOException, Command] = {

    putStrLn("please enter a command") *>
      getStrLn
        .flatMap(line => IO.fromOption(Command.fromString(line)))
        .tapError(_ => putStrLn(s"enter 'quit' or 'fib <n>'"))  // peek at error with interrupting program flow
        .eventually
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (for {
      _          <- putStrLn("How many fibers would you like to use for calculations?")
      fiberCount <- getNumber
      // async back pressured queue
      q <- Queue.bounded[Int](3)
      // just describes what a worker does
      worker = (for {
        n   <- q.take
        num <- fib(n)
        _   <- putStrLn(s"Fib number $n is $num")
      } yield ()).forever

      _ <- ZIO.forkAll_(List.fill(fiberCount)(worker)) // make 'fiberCount' copies of worker and fork them all onto different fibers
      _ <- promptCommand.flatMap {
            case Command.Quit          => ZIO.fail(())
            case Command.ComputeFib(n) => q.offer(n)
          }.forever.ignore
    } yield ()).exited
}
