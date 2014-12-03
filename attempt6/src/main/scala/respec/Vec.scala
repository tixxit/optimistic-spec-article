package respec

import scala.collection.mutable.Builder
import reflect.ClassTag

sealed abstract class Vec[A] {
  def size: Int
  def apply(n: Int): A
  def map[B: ClassTag](f: A => B): Vec[B]
}

object Vec {
  def apply[A](elems: Array[A]): Vec[A] = VecImpl(elems)
}

final case class VecImpl[T](elems: Array[T]) extends Vec[T] {
  def size: Int = elems.length
  def apply(i: Int): T = elems(i)
  def map[B: ClassTag](f: T => B): Vec[B] = {
    val a = new Array[B](elems.length)
    var i = 0
    while (i < elems.length) {
      a(i) = f(elems(i))
      i += 1
    }
    VecImpl(a)
  }
}
