package net.degoes.zio

import java.io.{ File, IOException }

import zio._
import zio.console.{ Console, putStrLn, _ }

import scala.util.Try

/* testing in a functional effect environment.
 * Demonstrates code that potentially tricky to test */
object App12 extends App with Helpers {

  final def putStrLn(line: String): UIO[Unit] = UIO(println(line))

  /* even though we know the effect is the same, this won't succeed. effects cannot be compared */
  putStrLn("this") == putStrLn("this")

  sealed trait ShellCommand
  object ShellCommand {
    final case class Ls(path: Option[String]) extends ShellCommand
    final case class Cd(path: Option[String]) extends ShellCommand
    case object Pwd                           extends ShellCommand
    case object Quit                          extends ShellCommand

    def fromString(input: String): Either[String, ShellCommand] =
      input match {
        case x if x.toLowerCase == "exit"          => Right(Quit)
        case x if x.toLowerCase == "ls"            => Right(Ls(None))
        case x if x.toLowerCase startsWith ("ls ") => Right(Ls(Some(x.drop(3))))
        case x if x.toLowerCase startsWith ("cd ") => Right(Cd(Some(x.drop(3))))
        case x if x.toLowerCase == ("pwd")         => Right(Pwd)
      }
  }

  // IOException from getStrLn isn't recoverable so no sense in explicitly capturing and propagating it.
  // using .orDie lets us push it out as an unhandled fatal error
  lazy val userCommand: ZIO[Console, Nothing, ShellCommand] =
    for {
      in  <- getStrLn.orDie
      cmd <- ZIO.fromEither(ShellCommand.fromString(in)).tapError(putStrLn).orElse(userCommand)
    } yield cmd

  def ls(wd: File, path: Option[String]): ZIO[Console, Nothing, Unit] = {
    val target = path.fold(wd)(p => new File(wd, p))
    for {
      // Option .fold to handle possible null returned by File.list()
      list <- UIO(Option(target.list()).fold[List[String]](Nil)(_.toList))
      _    <- ZIO.foreach(list)(putStrLn)

    } yield ()
  }

  def pwd(wd: File): ZIO[Console, Nothing, Unit] = putStrLn(s"${wd.getPath}")
  def cd(wd: Ref[File], path: Option[String]): ZIO[Console, Nothing, Unit] =
    path match {
      case None    => ZIO.succeed()
      case Some(p) => wd.update(wdFile => new File(wdFile, p)) *> ZIO.succeed()
    }

  def consoleLoop(wd: Ref[File]): ZIO[Console, Nothing, Unit] = {
    import net.degoes.zio.App12.ShellCommand._

    for {
      _       <- putStr("prompt>")
      command <- userCommand
      wdFile  <- wd.get
      loop <- command match {
               case Ls(path) => ls(wdFile, path) as true
               case Cd(path) => cd(wd, path) as true
               case Pwd      => pwd(wdFile) as true
               case Quit     => putStrLn("Exiting...") as false
             }
      _ <- if (loop) consoleLoop(wd) else ZIO.unit
    } yield ()
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (for {
      wdFile <- UIO(new File("."))
      wd     <- Ref.make(wdFile)
      _      <- consoleLoop(wd)
    } yield ()).exited
}
