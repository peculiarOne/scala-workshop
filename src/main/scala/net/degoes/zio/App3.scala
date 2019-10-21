package net.degoes.zio

import zio.{App, Task, UIO, ZIO}
import zio.console._


object App3 extends App with Helpers {

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    (for {
      _ <- putStrLn("name?")
      name <- getStrLn
      _ <- putStrLn(s"hello $name")
    } yield ()).exited
  }
}
