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

  private[respec] def fromVector[A](vec: Vector[A]): Vec[A] = {
    if (vec.forall(_.isInstanceOf[Double])) {
      val elems = vec.asInstanceOf[Vector[Double]].toArray
      DoubleVec(elems).asInstanceOf[Vec[A]]
    } else {
      GenericVec(vec)
    }
  }
}

final case class DoubleVec(elems: Array[Double]) extends Vec[Double] {
  def size: Int = elems.length
  def apply(i: Int): Double = elems(i)
  def map[B](f: Double => B): Vec[B] = {
    val bldr = Vector.newBuilder[B]
    var i = 0
    while (i < elems.length) {
      bldr += f(elems(i))
      i += 1
    }
    Vec.fromVector(bldr.result())
  }
}

case class GenericVec[A](elems: Vector[A]) extends Vec[A] {
  def size: Int = elems.size
  def apply(i: Int): A = elems(i)
  def map[B](f: A => B): Vec[B] = Vec.fromVector(elems.map(f))
}
