Optimistic (Re)specialization: Attempt 6
========================================

In a [previous article][Original] I showed a way of retaining the performance
of code that makes heavy use of `ClassTag`s and `@specialized` without having
to litter our public APIs with `@specialized` and `ClassTag`s. Shortly after
that article was posted, I got a PR from Vlad Ureche which added a new version
of `Vec` that uses the awesome [miniboxing compiler plugin][Minibox]. The
results were too good not to share.

If you haven't [read the previous article][Original] yet, I'd suggest reading
it now to gain some insight into the context of this post.

Attempt 6: Use the Miniboxing Plugin
------------------------------------

At a highlevel, miniboxing (`@miniboxed`) is a replacement for specialization
(`@specialized`). It let's us write generic Scala code without forcing us to box
primitives types. However, it is better than specialization in almost every
way. The key thing for me, though, is that it

  1) fixes many gotcha areas with @specialized where your code is silently deoptimized (such as inheriting
from an @specialized class or abstract class), and
  2) doesn't silently deoptimize your code!

This last bit is very important. One of the worst parts of using @specialized
is that there is no way of knowing if your code is using the @specialized
version of a class or method or using the generic version. You're left manually
inspecting the bytecode. Compounding this is all the annoying [quirks of
specialization][Quirks] which cause your code to be silently deoptimized.
Honestly, if you use @specialized and you haven't looked at the bytecode, I'd
bet dollars to donuts your code is not using specialization where you think it
should be. One of the key drivers for the optimizations in this post was that I
simply couldn't rely on @specialized to actually work. Miniboxing solves this
by aggressively warning you of places in your code that would use the
deoptimized generic version for primitive types. It'll even let you rewrite
your code, sprinkling in `@miniboxed`s to ensure your code is using the optimal
path.

More recently though, and relevant to this blog post, is the inclusion of a new
`MbArray[A]` type in the miniboxing plugin which is similar to a generic
`Array[A]` type, but uses an `Array[AnyRef]` in the generic case and an
`Array[Long]` or `Array[Double]` for primitives and, most importantly, doesn't
require any `ClassTag`s. So, we get good cache locality for primitive types
like an `Array`, but don't have to litter our code with `ClassTag`s everywhere.
If we use this type to back our `Vec` type, then we should retain the performance
characteristics of primitive arrays. Here is Vlad's implementation!

```scala
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
```

As noted, this does require the addition of the `@minboxed` annotation, but
given that miniboxing solves the silent deoptimization problem, I don't think
this is much of an issue anymore. Anyways, let's see the numbers:

```
> attempt6/benchmark:benchmark
...
[info] Benchmark                                       Mode  Samples        Score  Score error  Units
[info] r.VecMapBenchmark.squareDoubleArrayWithLoop    thrpt       20  1064975.131    28273.980  ops/s
[info] r.VecMapBenchmark.squareDoubleArrayWithMap     thrpt       20   105983.664     2003.303  ops/s
[info] r.VecMapBenchmark.squareDoubleVec              thrpt       20  1030329.272    24779.150  ops/s
```

Pretty much bang on! It takes a wee hit - I suspect due to treating the
primitives uniformly (thus requiring some munging of bits) - but it is
essentially negligible. So, it achieves similar performance to the while loop,
but without requiring all the ugliness of [Attempt 5][Attempt 5]. Unlike
`@specialized`,I feel confident I can rely on `@miniboxed` to not silently
deoptimize my code. Additionally, it removes a lot of the bytecode bloat
associated with specialized code, so is fairly safe to sprinkly liberally
throughout your code.

[Original]: http://io.pellucid.com/blog/optimistic-respecialization
[Minibox]: http://scala-miniboxing.org/
[Quirks]: http://axel22.github.io/2013/11/03/specialization-quirks.html
[Attempt 5]: http://io.pellucid.com/blog/optimistic-respecialization#attempt-5-exploiting-specialized
