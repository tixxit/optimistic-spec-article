package respec

final class Vec[@miniboxed T](elems: MbArray[T]) {
  def size: Int = elems.length
  def apply(i: Int): T = elems(i)
  def map[@miniboxed B](f: T => B): Vec[B] = {
    val a = MbArray.empty[B](elems.length)
    var i = 0
    while (i < elems.length) {
      a(i) = f(elems(i))
      i += 1
    }
    new Vec(a)
  }
}

object Vec {
  def apply[@miniboxed A](elems: Array[A]): Vec[A] = new Vec(MbArray.clone(elems))
}
