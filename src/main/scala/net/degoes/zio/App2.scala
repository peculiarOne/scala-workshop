package  net.degoes.zio

import zio.{App, Task, UIO, ZIO}

import scala.io.StdIn

trait Helpers {
  implicit class ZIOExtentions[R,E,A](zio: ZIO[R,E,A]) {
    val exited: ZIO[R, Nothing, Int] = zio.fold(_ => 1, _ => 0)
  }
}

object App2 extends App with Helpers {

  val procedural: ZIO[Any, Throwable, Unit] = {
    Task(println("Name?")).flatMap(_ => {
      Task(StdIn.readLine()).flatMap(name => {
        Task(println(s"hello $name"))
      })
    })
  }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    (for {
      _ <- Task(println("name?"))
      name <- Task(StdIn.readLine())
      _ <- Task(println(s"hello $name"))
    } yield ()).exited
  }
}
