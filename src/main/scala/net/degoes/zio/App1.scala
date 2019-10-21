package net.degoes.zio

import zio.{App, DefaultRuntime, Task, UIO, ZIO}

object App1 extends App {

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    // ZIO [R, E, A]
    // R = environment required
    // E = type of errors that may occur
    // A = type of success return value
    // Task[A] = ZIO [Any, Throwable, A]
    // UIO [A] = ZIO [Any, Nothing, A]
    // IO [E, A] = ZIO [Any, E, A]
    // Type Nothing - Type that has no member values. There are no values of type Nothing
    //              - Subtype of every other type
    // mental model: zio effect is like a R => Either[E,A]

    //  .as(x) is a alias for .map(_ => x)
    Task(print("Hello world!")).as(0) orElse Task.succeed(1)
    Task(print("Hello world!")).fold( _ => 1, _ => 0)
    // foldM must return an effect (M variant of functions is a common pattern)
    Task(print("Hello world!")).foldM( _ => Task.succeed(1), _ => Task.succeed(0))
    // .ignore throw away failure and succeed
    Task(print("Hello world!")).ignore.as(0)

    Task {
      println("hello world")
      throw new RuntimeException("Ahhh!")
    } foldM(
      _ => UIO(println("failed")).as(1),
      _ => UIO(printf("succeeded")).as(0)
    )
  }
}
