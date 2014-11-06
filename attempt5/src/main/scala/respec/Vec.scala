package respec

import scala.collection.mutable.Builder

sealed abstract class Vec[A] {
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
    if (elems.length == 0) {
      GenericVec[B](Vector.empty)
    } else {

      // The generic, deoptimized case.
      def genericMap(bldr: Builder[B, Vector[B]], i: Int): Vec[B] =
        if (i < elems.length) {
          bldr += f(elems(i))
          genericMap(bldr, i + 1)
        } else GenericVec(bldr.result())

      // The specialized, optimized case.
      def doubleMap(xs: Array[Double]): Vec[B] = {
        // Optimistically assume return type is Double.
        val fDD = f.asInstanceOf[Double => Double]
        var i = 1
        try {
          while (i < xs.length) {
            xs(i) = fDD(elems(i))
            i += 1
          }
          DoubleVec(xs).asInstanceOf[Vec[B]]
        } catch { case (_: ClassCastException) =>
          // We were too optimistic, deoptimize.
          val bldr = Vector.newBuilder[B]
          (0 until i) foreach { j =>
            bldr += xs(j).asInstanceOf[B]
          }
          genericMap(bldr, i)
        }
      }

      f(elems(0)) match {
        case (x: Double) =>
          val xs = new Array[Double](elems.length)
          xs(0) = x
          doubleMap(xs)

        // We could support additional specializations here, eg.
        // case (x: Int) =>
        //   ...

        case x =>
          val bldr = Vector.newBuilder[B]
          bldr += x
          genericMap(bldr, 1)
      }
    }
  }
}

case class GenericVec[A](elems: Vector[A]) extends Vec[A] {
  def size: Int = elems.size
  def apply(i: Int): A = elems(i)
  def map[B](f: A => B): Vec[B] = {
    if (elems.length == 0) {
      GenericVec[B](Vector.empty)
    } else {

      // The generic, deoptimized case.
      def genericMap(bldr: Builder[B, Vector[B]], i: Int): Vec[B] =
        if (i < elems.length) {
          bldr += f(elems(i))
          genericMap(bldr, i + 1)
        } else GenericVec(bldr.result())

      // The specialized, optimized case.
      def doubleMap(xs: Array[Double]): Vec[B] = {
        // Optimistically assume return type is Double.
        val fLD = f.asInstanceOf[A => Double]
        var i = 1
        try {
          while (i < xs.length) {
            xs(i) = fLD(elems(i))
            i += 1
          }
          DoubleVec(xs).asInstanceOf[Vec[B]]
        } catch { case (_: ClassCastException) =>
          // We were too optimistic, deoptimize.
          val bldr = Vector.newBuilder[B]
          (0 until i) foreach { j =>
            bldr += xs(j).asInstanceOf[B]
          }
          genericMap(bldr, i)
        }
      }

      f(elems(0)) match {
        case (x: Double) =>
          val xs = new Array[Double](elems.size)
          xs(0)
          doubleMap(xs)

        case x =>
          val bldr = Vector.newBuilder[B]
          bldr += x
          genericMap(bldr, 1)
      }
    }
  }
}
