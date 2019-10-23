package net.degoes.zio

import zio.{ App, UIO, ZEnv, ZIO }
import zio.stm.{ STM, TRef }

import scala.collection.immutable.{ Queue => ScalaQueue }

/* STM Software Transactional Memory.
 * Composable alternative to locking
 * Making changes to some shared state (held in a TRef)
 * Grants database like transaction behaviour
 *  */
object App15 extends App with Helpers {
  import zio.stm._

  final case class ConcurrentQueue[A] private (capacity: Int, ref: TRef[ScalaQueue[A]]) {
    def take: UIO[A] =
      (for {
        queue <- ref.get
        a     <- queue.headOption.fold[STM[Nothing, A]](STM.retry)(a => STM.succeed(a))
        _     <- ref.update(_.drop(1))

      } yield a).commit

    /* when calling this in production code you'd probably want to fork the returned effect
     * so it doesn't block the main fiber.
     * You can also use standard ZIO timeouts and other interruptions */
    def offer(a: A): UIO[Unit] =
      (
        for {
          queue <- ref.get
//    _ <- if (queue.size > capacity) STM.retry else STM.unit
          _ <- STM.check(queue.size <= capacity) // suspends until check passes
          _ <- ref.set(queue.enqueue(a))
        } yield ()
      ).commit
  }
  object ConcurrentQueue {
    def make[A](n: Int): UIO[ConcurrentQueue[A]] =
      for {
        ref <- TRef.make(ScalaQueue.empty[A]).commit
      } yield new ConcurrentQueue[A](n, ref)
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    ZIO.unit.exited
}
