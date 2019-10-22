package net.degoes.zio

import java.io.{ File, IOException }

import zio._
import zio.console.{ Console, _ }

/* example of using tagless final to provide differing implementations of Console / Shell for testing.
 * Lots of complexity here, lots of things to know, reasonable amount of boiler plate.
 * You can use types to indicate which effecting methods your functions may call. This however is down to your development
 * team accurately describing them, and ensuring that no effecting methods are called without types being provided
 * (the scala compiler won't do anything to help you here) */
//object App14 extends App with Helpers {
//
//  final def putStrLn(line: String): UIO[Unit] = UIO(println(line))
//
//  /* even though we know the effect is the same, this won't succeed. effects cannot be compared */
//  putStrLn("this") == putStrLn("this")
//
//  sealed trait ShellCommand
//
//  object ShellCommand {
//
//    final case class Ls(path: Option[String]) extends ShellCommand
//
//    final case class Cd(path: Option[String]) extends ShellCommand
//
//    case object Pwd extends ShellCommand
//
//    case object Quit extends ShellCommand
//
//    def fromString(input: String): Either[String, ShellCommand] =
//      input match {
//        case x if x.toLowerCase == "exit"          => Right(Quit)
//        case x if x.toLowerCase == "ls"            => Right(Ls(None))
//        case x if x.toLowerCase startsWith ("ls ") => Right(Ls(Some(x.drop(3))))
//        case x if x.toLowerCase startsWith ("cd ") => Right(Cd(Some(x.drop(3))))
//        case x if x.toLowerCase == ("pwd")         => Right(Pwd)
//      }
//  }
//
//  trait Console[F[_]] {
//    val getStrLn: F[String]
//    def putStrLn(line: String): F[Unit]
//    def putStr(line: String): F[Unit]
//  }
//  object Console {
//    def apply[F[_]](implicit console: Console[F]): Console[F] = console
//  }
//  trait Shell[F[_]] {
//    def listChildren(file: File): F[List[String]]
//
//    val initialWorkingDirectory: F[File]
//  }
//  object Shell {
//    def apply[F[_]](implicit shell: Shell[F]): Shell[F] = shell
//  }
//  trait Effect[F[_]] {
//    def fmap[A, B](fa: F[A])(f: A => B): F[B]
//    def bind[A, B](fa: F[A])(f: A => F[B]): F[B]
//    def pure[A](a: A): F[A]
//  }
//  object Effect {
//    def apply[F[_]](implicit effect: Effect[F]): Effect[F] = effect
//
//    def pure[F[_]: Effect, A](a: A): F[A] = Effect[F].pure(a)
//  }
//  implicit class EffectSyntax[F[_], A](fa: F[A]) {
//    def map[B](f: A => B)(implicit effect: Effect[F]): F[B] =
//      effect.fmap(fa)(f)
//    def flatMap[B](f: A => F[B])(implicit effect: Effect[F]): F[B] =
//      effect.bind(fa)(f)
//  }
//
//  def userCommand[F[_]: Effect: Console]: F[ShellCommand] =
//    for {
//      line <- Console[F].getStrLn
//      command <- ShellCommand.fromString(line) match {
//        case Right(command) => Effect.pure(command)
//        case Left(e) => Console[F].putStrLn(e).flatMap(_ => userCommand[F])
//      }
//    } yield command
////  object Shell {
////
////    trait Service {
////      def listChildren(file: File): UIO[List[String]]
////
////      val initialWorkingDirectory: UIO[File]
////    }
////
////    trait Live extends Shell {
////      val shell: Service = new Service {
////        def listChildren(file: File): UIO[List[String]] = UIO(Option(file.list()).fold[List[String]](Nil)(_.toList))
////
////        def getAbsolutePath(file: File): UIO[File] = UIO(file.getAbsoluteFile)
////
////        override val initialWorkingDirectory: UIO[File] = UIO(new File(".").getAbsoluteFile)
////      }
////    }
////
////    object Live extends Live
////
////    // helper function to deal with Shell resolution
////    def listChildren(file: File): ZIO[Shell, Nothing, List[String]] =
////      // long form
////      //      for {
////      //        env <- ZIO.environment[Shell]
////      //        children <- env.shell.listChildren(file)
////      //      } yield children
////      // short form
////      ZIO.accessM[Shell](_.shell.listChildren(file))
////
////    val initialWorkingDirectory: UIO[File] = UIO(new File(".").getAbsoluteFile)
////
////    def makeTest(initWd: File, map: Map[File, List[String]]): Service =
////      new Service {
////        override def listChildren(file: File): UIO[List[String]] =
////          ZIO.fromOption(map.get(file)) orElse (ZIO.succeed(Nil))
////
////        override val initialWorkingDirectory: UIO[File] = UIO.succeed(initWd)
////      }
////  }
//
//  final case class TestConsole(input: List[String], output: List[String]) {
//    def putStrLn(line: String): TestConsole = putStr(line + "\n")
//
//    def putStr(line: String): TestConsole = copy(output = line :: output)
//
//    def getStrLn: (String, TestConsole) = (input.head, putStrLn(input.head).copy(input = input.drop(1)))
//
//  }
//
//  def makeTestConsole(ref: Ref[TestConsole]): Console.Service[Any] =
//    new Console.Service[Any] {
//      override def putStr(line: String): ZIO[Any, Nothing, Unit] = ref.update(_.putStr(line)) as ()
//
//      override def putStrLn(line: String): ZIO[Any, Nothing, Unit] = ref.update(_.putStrLn(line)) as ()
//
//      override val getStrLn: ZIO[Any, IOException, String] = ref.modify(_.getStrLn)
//    }
//
//
//  def ls(wd: File, path: Option[String]): ZIO[Console with Shell, Nothing, Unit] = {
//    val target = path.fold(wd)(p => new File(wd, p))
//    for {
//      // Option .fold to handle possible null returned by File.list()
//      //      list <- UIO(Option(target.list()).fold[List[String]](Nil)(_.toList))
//      list <- ((Shell.listChildren(target)))
//      _    <- ZIO.foreach(list)(putStrLn)
//
//    } yield ()
//  }
//
//  def pwd(wd: File): ZIO[Console, Nothing, Unit] = putStrLn(s"${wd.getPath}")
//
//  def cd(wd: Ref[File], path: Option[String]): ZIO[Console, Nothing, Unit] =
//    path match {
//      case None    => ZIO.succeed()
//      case Some(p) => wd.update(wdFile => new File(wdFile, p)) *> ZIO.succeed()
//    }
//
//  def consoleLoop(wd: Ref[File]): ZIO[Console with Shell, Nothing, Unit] = {
//    import ShellCommand._
//
//    for {
//      _       <- putStr("prompt>")
//      command <- userCommand
//      wdFile  <- wd.get
//      loop <- command match {
//               case Ls(path) => ls(wdFile, path) as true
//               case Cd(path) => cd(wd, path) as true
//               case Pwd      => pwd(wdFile) as true
//               case Quit     => putStrLn("Exiting...") as false
//             }
//      // this kind of recursion will eventually blow the heap.
//      // you can't implement tail recursion within a for-comprehension because it performs a map() on the final result.
//      // could rewrite this to be tail recursive by writing this as a series of flatMap() commands and not doing
//      // any processing on the final result
//      _ <- if (loop) consoleLoop(wd) else ZIO.unit
//    } yield ()
//  }
//
//  val shell = for {
//    wdFile <- Shell.initialWorkingDirectory
//    wd     <- Ref.make(wdFile)
//    _      <- consoleLoop(wd)
//  } yield ()
//
//  def runScenario(wd: File, map: Map[File, List[String]], input: List[String]): UIO[List[String]] =
//    for {
//      ref      <- Ref.make(TestConsole(input ++ List("exit"), Nil))
//      shell0   = Shell.makeTest(wd, map)
//      console0 = makeTestConsole(ref)
//      testEnv = new Shell with Console {
//        val console = console0
//        val shell   = shell0
//      }
//      _     <- shell.provide(testEnv)
//      lines <- ref.get.map(_.output.reverse)
//    } yield lines
//
//  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
//    val wd          = new File("root")
//    val child1      = new File(wd, "child1")
//    val child2      = new File(wd, "child2")
//    val grandchild1 = new File(child2, "grandchild1")
//    val map =
//      Map(wd -> List("child1", "child2"), child1 -> Nil, child2 -> List("grandchild1"), grandchild1 -> List())
//    (for {
//      lines <- runScenario(wd, map, List("pwd", "ls", "cd child2", "ls"))
//      _     <- putStrLn(lines.mkString(""))
//    } yield ()).provide(new Console.Live with Shell.Live).exited
//  }
//}
