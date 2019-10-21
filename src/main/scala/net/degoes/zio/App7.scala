package net.degoes.zio

import java.io.{File, FileInputStream, IOException, InputStream}
import java.util.concurrent.ScheduledThreadPoolExecutor

import zio._
import zio.blocking.{Blocking, effectBlocking}
import zio.console.{Console, _}

import scala.util.Try


object App7 extends App with Helpers {

  def parseInt(s: String): Option[Int] = Try(s.toInt).toOption

  // version that converts our option into a ZIO effect
  def parseIntM(s: String): ZIO[Any, Unit, Int] = ZIO.fromOption(parseInt(s))

  def eventually[R,E,A](zio: ZIO[R,E,A]): ZIO[R, Nothing, A] =
    zio orElse(eventually(zio))

  def retry[R,E,A](n: Int, zio: ZIO[R,E,A]): ZIO[R,E,A] = {
    if (n <= 1) zio else zio orElse(retry(n - 1, zio))
  }


  // case class with private contructor
  final case class Input private (is: InputStream) {
    val close: IO[IOException, Unit] = IO(is.close()).refineToOrDie[IOException]
    /* .lock(???) lets you restrict the thread pool that an effect is executed on.
    * ZIO will handle jumping in and out of the restricted context seamlessly */
//    val close: IO[IOException, Unit] = IO(is.close()).refineToOrDie[IOException].lock(???)

    // Chunk is essentially an immutable wrapper around an Array
    def read(size: Int = 1024): ZIO[Blocking, IOException, Option[Chunk[Byte]]] = {
      // effectBlocking : run all this on a special blocking thread pool
      // the default context you get with a ZEnv provides a properly configured blocking thread pool
      effectBlocking {
        val array = new Array[Byte](size)
        val bytesRead = is.read(array)
        if (bytesRead == -1) None
        else Some(Chunk.fromArray(array).take(bytesRead))
      }.refineToOrDie[IOException]
    }
  }

  object Input {
    def open(file: File): IO[IOException, Input] = {
      IO(new Input(new FileInputStream((file)))).refineToOrDie[IOException]
    }

  }

  override def run(args: List[String]): ZIO[Console, Nothing, Int] = {
    if (args.isEmpty) {
      console.putStrLn("need to provide path to a file")
      UIO.succeed(1)
    }
    (for {
      input <- Input.open(new File(args.head))
      _ <- input.read().flatMap{
        case None => IO.fail(Unit)
        case Some(chunk) => putStr(chunk.mkString)
      }.forever orElse IO.unit
      // if something blows up before here (unhandled exception) then input.close will never happen
      _ <- input.close

      _ <- putStrLn("")
    } yield() ).exited
  }
}
