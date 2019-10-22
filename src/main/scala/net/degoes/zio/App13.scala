package net.degoes.zio

import java.io.{ File, IOException }

import zio._
import zio.console.{ Console, _ }

/* testing in a functional effect environment */
object App13 extends App with Helpers {
  import zio.console._

  sealed trait ShellCommand
  object ShellCommand {
    final case class Ls(path: Option[String]) extends ShellCommand
    final case class Cd(path: String)         extends ShellCommand
    case object Pwd                           extends ShellCommand
    case object Quit                          extends ShellCommand

    def fromString(input: String): Either[String, ShellCommand] =
      input.trim match {
        case x if x.toLowerCase == "ls"          => Right(Ls(None))
        case x if x.toLowerCase startsWith "ls " => Right(Ls(Some(x.drop(3))))
        case x if x.toLowerCase startsWith "cd " => Right(Cd(x.drop(3)))
        case x if x.toLowerCase == "pwd"         => Right(Pwd)
        case x if x.toLowerCase == "quit"        => Right(Quit)
        case x if x.toLowerCase == "exit"        => Right(Quit)
        case _                                   => Left("Expected command but received: " + input)
      }
  }

  import ShellCommand._
  import java.io.File

  trait Shell {
    def shell: Shell.Service
  }
  object Shell {
    trait Service {
      def listChildren(file: File): UIO[List[String]]

      val initialWorkingDirectory: UIO[File]
    }
    trait Live extends Shell {
      val shell = new Service {
        def listChildren(file: File): UIO[List[String]] =
          UIO(Option(file.list()).fold[List[String]](Nil)(_.toList))

        val initialWorkingDirectory: UIO[File] = UIO(new File(".").getAbsoluteFile())
      }
    }
    object Live extends Live

    def makeTest(initWd: File, map: Map[File, List[String]]): Service =
      new Service {
        def listChildren(file: File): UIO[List[String]] =
          ZIO.fromOption(map.get(file)) orElse ZIO.succeed(Nil)

        val initialWorkingDirectory: UIO[File] = UIO.succeed(initWd)
      }

    def listChildren(file: File): ZIO[Shell, Nothing, List[String]] =
      ZIO.accessM[Shell](_.shell.listChildren(file))

    val initialWorkingDirectory: ZIO[Shell, Nothing, File] =
      ZIO.accessM[Shell](_.shell.initialWorkingDirectory)
  }

  lazy val userCommand: ZIO[Console, Nothing, ShellCommand] =
    for {
      line <- getStrLn.orDie
      command <- ZIO
                  .fromEither(ShellCommand.fromString(line))
                  .tapError(putStrLn(_)) orElse userCommand
    } yield command

  def ls(wd: File, childOpt: Option[String]) = {
    val target = childOpt.fold(wd)(p => new File(wd, p))

    for {
      children <- Shell.listChildren(target)
      _ <- ZIO.foreach(children.sorted) { child =>
            putStrLn(childOpt.fold(child)(_ + "/" + child))
          }
    } yield ()
  }

  def cd(wd: Ref[File], path: String) =
    wd.update(wdFile => new File(wdFile, path)) *>
      wd.get.flatMap(file => putStrLn(file.getPath()))

  def pwd(wd: File) = putStrLn(wd.getPath())

  def consoleLoop(wd: Ref[File]): ZIO[Console with Shell, Nothing, Unit] =
    putStr("ZIO Shell> ").flatMap(
      _ =>
        userCommand.flatMap(
          command =>
            wd.get.flatMap(
              wdFile =>
                (command match {
                  case Ls(path) => ls(wdFile, path) as true
                  case Cd(path) => cd(wd, path) as true
                  case Pwd      => pwd(wdFile) as true
                  case Quit     => putStrLn("Exiting...") as false
                }).flatMap(loop => if (loop) consoleLoop(wd) else ZIO.unit)
          )
      )
    )

  final case class TestConsole(input: List[String], output: List[String]) {
    final def putStrLn(line: String): TestConsole = putStr(line + "\n")

    final def putStr(line: String): TestConsole =
      copy(output = line :: output)

    final def getStrLn: (String, TestConsole) =
      (input.head, copy(input = input.drop(1)))
  }

  def makeTestConsole(ref: Ref[TestConsole]): Console.Service[Any] =
    new Console.Service[Any] {
      val getStrLn: ZIO[Any, IOException, String]         = ref.modify(_.getStrLn)
      def putStr(line: String): ZIO[Any, Nothing, Unit]   = ref.update(_.putStr(line)).unit
      def putStrLn(line: String): ZIO[Any, Nothing, Unit] = ref.update(_.putStrLn(line)).unit
    }

  val shell: ZIO[Shell with Console, Nothing, Unit] =
    for {
      wdFile <- Shell.initialWorkingDirectory
      wd     <- Ref.make(wdFile)
      _      <- consoleLoop(wd)
    } yield ()

  def runScenario(wd: File, map: Map[File, List[String]], input: List[String]): UIO[List[String]] =
    for {
      ref      <- Ref.make(TestConsole(input ++ List("quit"), Nil))
      shell0   = Shell.makeTest(wd, map)
      console0 = makeTestConsole(ref)
      testEnv = new Shell with Console {
        val console = console0
        val shell   = shell0
      }
      _     <- shell.provide(testEnv)
      lines <- ref.get.map(_.output.reverse)
    } yield lines

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (for {
      wdFile <- Shell.initialWorkingDirectory
      wd     <- Ref.make(wdFile)
      _      <- consoleLoop(wd)
    } yield ()).provide(new Console.Live with Shell.Live).exited
}
