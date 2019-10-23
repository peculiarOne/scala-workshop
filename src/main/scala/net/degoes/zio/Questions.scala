package net.degoes.zio

import zio._

import scala.collection.immutable.{ Queue => ScalaQueue }
import scala.concurrent.{ ExecutionContext, Future }

/* STM Software Transactional Memory.
 * Composable alternative to locking
 * Making changes to some shared state (held in a TRef)
 * Grants database like transaction behaviour
 *  */
object App16 extends App with Helpers {

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

  import zio.console._
  import zio.random._

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (for {
      _ <- putStrLn("start of example")
      v <- nextInt(100)
      _ <- putStrLn(s"number is $v")
      _ <- ZIO.fail("oh no!!")
    } yield ()).catchAllCause(cause => putStrLn(cause.prettyPrint)).exited
}

object App17 extends App with Helpers {
  def legacyCode(s: String)(implicit ec: ExecutionContext): Future[Unit] = Future.successful(s).map(_ => ())

  import zio.console._

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    ZIO.fromFuture { implicit ec =>
      legacyCode("foo")
    }
  }.exited

  // turn effectual code back into a standard Future
  val f = unsafeRunToFuture(putStrLn("something").provide(Console.Live))
}

object App18 extends App with Helpers {
  import zio.console._
  import zio.random._
  import zio.duration._

  /**
   * Produce a jittered schedule that first does exponential spacing (starting
   * from 10 milliseconds), but then after the spacing reaches 60 seconds,
   * switches over to fixed spacing of 60 seconds between recurrences, but will
   * only do that for up to 100 times, and produce a list of the inputs to
   * the schedule.
   */
  def exampleSchedule[A]: ZSchedule[Random, A, List[A]] =
    // 'andThen' combine two schedules to run sequentially
    (Schedule.exponential(10.millis).whileOutput(_ <= 60.seconds) andThen
      // '&&' combined largest sleep with smallest repeat to produce a new Schedule
      (Schedule.spaced(60.seconds) && Schedule.recurs(100)).jittered) *> Schedule.identity.collectAll

  val flakyEffect: ZIO[Console with Random, String, Unit] = random.nextBoolean.flatMap {
    case true  => putStrLn("hello")
    case false => ZIO.fail("fail")
  }

  val reliableEffect          = putStrLn("worked")
  val moreReliableFlakyEffect = flakyEffect.retry(exampleSchedule[String])

  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    ZIO.unit.exited
}
