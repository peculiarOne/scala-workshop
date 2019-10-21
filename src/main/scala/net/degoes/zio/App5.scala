package net.degoes.zio

import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor, TimeUnit}

import net.degoes.zio.App5.putStrLn
import zio.console._
import zio.{App, IO, Task, UIO, ZIO}

import scala.io.StdIn
import scala.util.{Random, Try}


object App5 extends App with Helpers {

  def parseInt(s: String): Option[Int] = Try(s.toInt).toOption

  // version that converts our option into a ZIO effect
  def parseIntM(s: String): ZIO[Any, Unit, Int] = ZIO.fromOption(parseInt(s))

  def eventually[R,E,A](zio: ZIO[R,E,A]): ZIO[R, Nothing, A] =
    zio orElse(eventually(zio))

  def retry[R,E,A](n: Int, zio: ZIO[R,E,A]): ZIO[R,E,A] = {
    if (n <= 1) zio else zio orElse(retry(n - 1, zio))
  }


  def putStrLn(string: String): UIO[Unit] = UIO(println(string))
  // val because the description of what's in the effect never changes
  val getStrLn: Task[String] = Task(StdIn.readLine())
  val nextDouble: UIO[Double] = UIO(Random.nextDouble())

  private[this] val scheduler = new ScheduledThreadPoolExecutor(1)

  // normal use of scheduler
  scheduler.schedule(new Runnable {
    override def run(): Unit = println("hello world")
  }, 10, TimeUnit.SECONDS)

  // doesn't block and threads in the ZIO side of things
  // more or less the same as you get when calling ZIO.sleep()
  // effectAsync helps you deal with non-lazy code that uses callbacks
  def sleep(n: Long, unit: TimeUnit): UIO[Unit] = {
    UIO.effectAsync[Unit]{ callback => { // callback comes from ZIO and is just used so we can notify when our async operation is done
      scheduler.schedule(new Runnable {
        override def run(): Unit = callback(UIO.unit)
      }, n, unit)
    }}
  }

  /* Java API with unpleasant multiple callbacks, plus a nice ZIO wrapper that get of that while still returning
  * success or error state */
  def javaApi(file: String)(failure: Throwable => Unit, success: Array[Byte] => Unit): Unit = ???

  def zioJavaApi(file: String): IO[Throwable, Array[Byte]] = {
    IO.effectAsync({ callback => {
      javaApi(file)(
        failure = f => callback(IO.fail(f)),
        success = bs => callback(IO.succeed(bs)))
    }})
  }

  override def run(args: List[String]): UIO[Int] = {
    (for {
      dbl <- nextDouble
      millis = (dbl * 1000.0).toLong
      _ <- putStrLn(s"about to sleep for $millis milliseconds")
      _ <- sleep(millis, TimeUnit.MILLISECONDS)
      _ <- putStrLn("Finished sleeping")
      _ <- putStrLn("How many seconds should we sleep for?")
      time <- getStrLn.flatMap(s => Task(s.toDouble)).tapError(_ => putStrLn("Only doubles allowed")).eventually
        _ <- putStrLn(s"about to sleep for $time seconds")
      _ <- sleep((time * 1000).toLong, TimeUnit.MILLISECONDS)
      _ <- putStrLn("Finished sleeping")
      _ <- UIO(scheduler.shutdown())
    } yield() ).exited
  }
}
