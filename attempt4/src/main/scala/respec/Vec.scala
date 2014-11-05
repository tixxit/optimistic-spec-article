package respec

import scala.collection.mutable.Builder

sealed abstract class Vec[@specialized(Double) A] {
  def size: Int
  def apply(n: Int): A
  def map[B](f: A => B): Vec[B]
}

object Vec {
  def apply[A](elems: Array[A]): Vec[A] = elems match {
    case (elems: Array[Double]) => DoubleVec(java.util.Arrays.copyOf(elems, elems.length))
    case _ => GenericVec(elems.toVector)
  }
}

final case class DoubleVec(elems: Array[Double]) extends Vec[Double] {
  def size: Int = elems.length
  def apply(i: Int): Double = elems(i)
  def map[B](f: Double => B): Vec[B] = {
    val bs = new Array[Double](elems.length)
    var i = 0
    try {
      while (i < elems.length) {
        bs(i) = f(elems(i)).asInstanceOf[Double]
        i += 1
      }
      DoubleVec(bs).asInstanceOf[Vec[B]]
    } catch { case (_: ClassCastException) =>
      val bldr = Vector.newBuilder[B]
      var j = 0
      while (j < i) {
        bldr += bs(j).asInstanceOf[B]
        j += 1
      }
      while (i < elems.length) {
        bldr += f(elems(i))
        i += 1
      }
      GenericVec(bldr.result())
    }
  }
}

case class GenericVec[A](elems: Vector[A]) extends Vec[A] {
  def size: Int = elems.size
  def apply(i: Int): A = elems(i)
  def map[B](f: A => B): Vec[B] = {
    val bs = new Array[Double](elems.length)
    var i = 0
    try {
      while (i < elems.length) {
        bs(i) = f(elems(i)).asInstanceOf[Double]
        i += 1
      }
      DoubleVec(bs).asInstanceOf[Vec[B]]
    } catch { case (_: ClassCastException) =>
      val bldr = Vector.newBuilder[B]
      var j = 0
      while (j < i) {
        bldr += bs(j).asInstanceOf[B]
        j += 1
      }
      while (i < elems.length) {
        bldr += f(elems(i))
        i += 1
      }
      GenericVec(bldr.result())
    }
  }
}
