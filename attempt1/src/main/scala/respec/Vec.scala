package respec

import scala.collection.mutable.Builder

sealed abstract class Vec[A] {
  def size: Int
  def apply(n: Int): A
  def map[B](f: A => B): Vec[B]
}

object Vec {
  def apply[A](elems: Array[A]): Vec[A] = GenericVec(elems.toVector)
}

case class GenericVec[A](elems: Vector[A]) extends Vec[A] {
  def size: Int = elems.size
  def apply(i: Int): A = elems(i)
  def map[B](f: A => B): Vec[B] = GenericVec(elems map f)
}
