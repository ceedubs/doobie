package doobie.free

import cats.~>
import cats.effect.Async
import cats.free.{ Free => FF } // alias because some algebras have an op called Free

import java.io.InputStream
import java.io.OutputStream
import java.sql.Blob

@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
object blob { module =>

  // Algebra of operations for Blob. Each accepts a visitor as an alternatie to pattern-matching.
  sealed trait BlobOp[A] {
    def visit[F[_]](v: BlobOp.Visitor[F]): F[A]
  }

  // Free monad over BlobOp.
  type BlobIO[A] = FF[BlobOp, A]

  // Module of instances and constructors of BlobOp.
  object BlobOp {

    // Given a Blob we can embed a BlobIO program in any algebra that understands embedding.
    implicit val BlobOpEmbeddable: Embeddable[BlobOp, Blob] =
      new Embeddable[BlobOp, Blob] {
        def embed[A](j: Blob, fa: FF[BlobOp, A]) = Embedded.Blob(j, fa)
      }

    // Interface for a natural tansformation BlobOp ~> F encoded via the visitor pattern.
    // This approach is much more efficient than pattern-matching for large algebras.
    trait Visitor[F[_]] extends (BlobOp ~> F) {
      final def apply[A](fa: BlobOp[A]): F[A] = fa.visit(this)

      // Common
      def raw[A](f: Blob => A): F[A]
      def embed[A](e: Embedded[A]): F[A]
      def delay[A](a: () => A): F[A]
      def suspend[A](a: () => BlobIO[A]): F[A]
      def handleErrorWith[A](fa: BlobIO[A], f: Throwable => BlobIO[A]): F[A]
      def raiseError[A](t: Throwable): F[A]
      def async[A](k: (Either[Throwable, A] => Unit) => Unit): F[A]

      // Blob
      def free: F[Unit]
      def getBinaryStream: F[InputStream]
      def getBinaryStream(a: Long, b: Long): F[InputStream]
      def getBytes(a: Long, b: Int): F[Array[Byte]]
      def length: F[Long]
      def position(a: Array[Byte], b: Long): F[Long]
      def position(a: Blob, b: Long): F[Long]
      def setBinaryStream(a: Long): F[OutputStream]
      def setBytes(a: Long, b: Array[Byte]): F[Int]
      def setBytes(a: Long, b: Array[Byte], c: Int, d: Int): F[Int]
      def truncate(a: Long): F[Unit]

    }

    // Common operations for all algebras.
    final case class Raw[A](f: Blob => A) extends BlobOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raw(f)
    }
    final case class Embed[A](e: Embedded[A]) extends BlobOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.embed(e)
    }
    final case class Delay[A](a: () => A) extends BlobOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.delay(a)
    }
    case class Suspend[A](a: () => BlobIO[A]) extends BlobOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.suspend(a)
    }
    case class HandleErrorWith[A](fa: BlobIO[A], f: Throwable => BlobIO[A]) extends BlobOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.handleErrorWith(fa, f)
    }
    case class RaiseError[A](t: Throwable) extends BlobOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raiseError(t)
    }
    case class Async1[A](k: (Either[Throwable, A] => Unit) => Unit) extends BlobOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.async(k)
    }

    // Blob-specific operations.
    final case object Free extends BlobOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.free
    }
    final case object GetBinaryStream extends BlobOp[InputStream] {
      def visit[F[_]](v: Visitor[F]) = v.getBinaryStream
    }
    final case class  GetBinaryStream1(a: Long, b: Long) extends BlobOp[InputStream] {
      def visit[F[_]](v: Visitor[F]) = v.getBinaryStream(a, b)
    }
    final case class  GetBytes(a: Long, b: Int) extends BlobOp[Array[Byte]] {
      def visit[F[_]](v: Visitor[F]) = v.getBytes(a, b)
    }
    final case object Length extends BlobOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.length
    }
    final case class  Position(a: Array[Byte], b: Long) extends BlobOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.position(a, b)
    }
    final case class  Position1(a: Blob, b: Long) extends BlobOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.position(a, b)
    }
    final case class  SetBinaryStream(a: Long) extends BlobOp[OutputStream] {
      def visit[F[_]](v: Visitor[F]) = v.setBinaryStream(a)
    }
    final case class  SetBytes(a: Long, b: Array[Byte]) extends BlobOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.setBytes(a, b)
    }
    final case class  SetBytes1(a: Long, b: Array[Byte], c: Int, d: Int) extends BlobOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.setBytes(a, b, c, d)
    }
    final case class  Truncate(a: Long) extends BlobOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.truncate(a)
    }

  }
  import BlobOp._

  // Smart constructors for operations common to all algebras.
  val unit: BlobIO[Unit] = FF.pure[BlobOp, Unit](())
  def raw[A](f: Blob => A): BlobIO[A] = FF.liftF(Raw(f))
  def embed[F[_], J, A](j: J, fa: FF[F, A])(implicit ev: Embeddable[F, J]): FF[BlobOp, A] = FF.liftF(Embed(ev.embed(j, fa)))
  def delay[A](a: => A): BlobIO[A] = FF.liftF(Delay(() => a))
  def suspend[A](a: => BlobIO[A]): BlobIO[A] = FF.liftF(Suspend(() => a))
  def handleErrorWith[A](fa: BlobIO[A], f: Throwable => BlobIO[A]): BlobIO[A] = FF.liftF[BlobOp, A](HandleErrorWith(fa, f))
  def raiseError[A](err: Throwable): BlobIO[A] = FF.liftF(RaiseError(err))
  def async[A](k: (Either[Throwable, A] => Unit) => Unit): BlobIO[A] = FF.liftF[BlobOp, A](Async1(k))

  // Smart constructors for Blob-specific operations.
  val free: BlobIO[Unit] = FF.liftF(Free)
  val getBinaryStream: BlobIO[InputStream] = FF.liftF(GetBinaryStream)
  def getBinaryStream(a: Long, b: Long): BlobIO[InputStream] = FF.liftF(GetBinaryStream1(a, b))
  def getBytes(a: Long, b: Int): BlobIO[Array[Byte]] = FF.liftF(GetBytes(a, b))
  val length: BlobIO[Long] = FF.liftF(Length)
  def position(a: Array[Byte], b: Long): BlobIO[Long] = FF.liftF(Position(a, b))
  def position(a: Blob, b: Long): BlobIO[Long] = FF.liftF(Position1(a, b))
  def setBinaryStream(a: Long): BlobIO[OutputStream] = FF.liftF(SetBinaryStream(a))
  def setBytes(a: Long, b: Array[Byte]): BlobIO[Int] = FF.liftF(SetBytes(a, b))
  def setBytes(a: Long, b: Array[Byte], c: Int, d: Int): BlobIO[Int] = FF.liftF(SetBytes1(a, b, c, d))
  def truncate(a: Long): BlobIO[Unit] = FF.liftF(Truncate(a))

  // BlobIO is an Async
  implicit val AsyncBlobIO: Async[BlobIO] =
    new Async[BlobIO] {
      val M = FF.catsFreeMonadForFree[BlobOp]
      def pure[A](x: A): BlobIO[A] = M.pure(x)
      def handleErrorWith[A](fa: BlobIO[A])(f: Throwable => BlobIO[A]): BlobIO[A] = module.handleErrorWith(fa, f)
      def raiseError[A](e: Throwable): BlobIO[A] = module.raiseError(e)
      def async[A](k: (Either[Throwable,A] => Unit) => Unit): BlobIO[A] = module.async(k)
      def flatMap[A, B](fa: BlobIO[A])(f: A => BlobIO[B]): BlobIO[B] = M.flatMap(fa)(f)
      def tailRecM[A, B](a: A)(f: A => BlobIO[Either[A, B]]): BlobIO[B] = M.tailRecM(a)(f)
      def suspend[A](thunk: => BlobIO[A]): BlobIO[A] = module.suspend(thunk)
    }

}
