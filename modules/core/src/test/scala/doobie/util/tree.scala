package doobie
package util

import shapeless._, shapeless.test._
import org.specs2.mutable.Specification

import cats.Monad
import cats.free.{ Free, Coyoneda }

object treespec extends Specification {

  "Partial Unification" should {

    "allow inference of Monad[Free[Coyoneda[F, ?], ?]]" in {
      trait Foo[A]
      Monad[Free[Coyoneda[Foo, ?], ?]]
      true
    }

  }

}