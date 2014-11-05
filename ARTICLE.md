Optimistic (Re)specialization
=============================

In this blog post we will explore a technique [Framian][Framian] uses to get
the kind of performance possible with liberal use of specialization and
`ClassTag`s, without relying on `@specialized` or `ClassTags` in our interface.

This will not be clear, concise, idiomatic code.  It will require code
duplication, reflection, and casts. You have been warned!

All of the code in this article is [provided as a working SBT project][Project].
You can follow along and run the benchmarks yourself. Just clone the project and
fire up SBT.

```
$ git clone git@github.com:tixxit/optimistic-spec-article.git
$ cd optimistic-spec-article
$ sbt
```

The Problem
-----------

To demonstrate the technique, we'll create a simple, non-specialized, generic
`Vec[A]` type that represents some vector, similar to Scala's built-in `Vector[A]`
or `Array[A]` types. This is the interface we must conform to:

```scala
sealed trait Vec[A] {
  def size: Int
  def apply(i: Int): A
  def map[B](f: A => B): Vec[B]
}

object Vec {
  def apply[A](elems: Array[A]): Vec[A] = ???
}
```

Our **goal** is to have `Vec`'s `map` method be *as fast as* a simple, manually
written while loop over an array of primitives, when `A` is a primtive type.
For the sake of size and sanity, we'll narrow "primitive types" to mean
`Double`. We'll start this off by encoding our goal as a benchmark (using the
fantastic JMH microbenchmarking library):

```scala
import org.openjdk.jmh.annotations.{ Benchmark, Scope, State }

class VecMapBenchmark {

  // Squares the values in a simple while loop that performs the computation inline.
  @Benchmark
  def squareDoubleArrayWithLoop(data: MapData) = {
    val xs = data.doubleData
    val ys = new Array[Double](xs.length)
    var i = 0
    while (i < xs.length) {
      val x = xs(i)
      ys(i) = x * x
      i += 1
    }
    ys
  }

  // Squares the values using Scala's `map` method on Array.
  @Benchmark
  def squareDoubleArrayWithMap(data: MapData) =
    data.doubleData.map(x => x * x)
 
  // Squares the values using our `map` method.
  @Benchmark
  def squareDoubleVec(data: MapData) =
    data.doubleVec.map(x => x * x)
}

@State(Scope.Benchmark)
class MapData {
  val size = 1000
  val rng = new Random(42)
  val doubleData: Array[Double] = Array.fill(size)(rng.nextDouble)
  val doubleVec: Vec[Double] = Vec(doubleData)
}
```

You'll note we have 3 benchmarks.

`squareDoubleArrayWithLoop`: This benchmark uses a while loop over an array,
squaring the elements directly. This provides our *upper bound*. This is the
absolute best case we could hope for. On the JVM, we really can't hope to be
faster than a straight while loop over an array with no function calls and only
primitive double arithmetic.

`squareDoubleArrayWithMap`: In this benchmark we simply use the `map` already
found on an `Array`. This provides our *lower bound*. This is the absolute
worst case that is acceptable, because if we're worse than `Array`'s `map`, why
bother with the new type `Vec`?

`squareDoubleVec`: This is our actual benchmark. The goal is to have this come
as close to `squareArrayWithLoop` as possible.

Attempt 1: Get It Working
-------------------------

Alright, let's get to work! We'll start with the simplest thing that works. At
first glance it may seem like we should just wrap an `Array[A]`, since then
we'll meet our lower bound goal (`squareArrayWithMap`) by definition. However,
we don't have access to any `ClassTag`s in the signature of `map`, so we can't
store the result of `map` in an array. We have to use something more generic:
Scala's built-in `Vector` data structure will suffice for now.

```scala
sealed trait Vec[A] {
  def size: Int
  def apply(i: Int): A
  def map[B](f: A => B): Vec[B]
}

object Vec {
  def apply[A](elems: Array[A]): Vec[A] = GenericVec(elems.toVector)
}

private case class GenericVec[A](elems: Vector[A]) {
  def size: Int = elems.size
  def apply(i: Int): A = elems(i)
  def map[B](f: A => B): Vec[B] = GenericVec(elems.map(f))
}
```

This is a pretty straight forward implementation that delegates all methods to
those on `Vector[A]`. This is our baseline. Let's put this implementation
through the ringer (benchmarks) and see what we get!

```
> attempt1/benchmark:benchmark
...
[info] Benchmark                                       Mode  Samples       Score  Score error  Units
[info] r.VecMapBenchmark.squareDoubleArrayWithLoop    thrpt       20  905074.223    60220.554  ops/s
[info] r.VecMapBenchmark.squareDoubleArrayWithMap     thrpt       20   85956.235     4521.496  ops/s
[info] r.VecMapBenchmark.squareDoubleVec              thrpt       20   92921.505     4895.042  ops/s
```

The **Score** is the number of ops/second (how many times can we execute the
benchmark in a second), so higher is better. Right away you can see the results
were quite abysmal.  Our `Vector` based version is 10x slower than a while
loop. We have a lot of catching up to do! On the plus side, we actually did
slightly better than our lowerbound, which is a bit surprising, but promising.

Attempt 2: Manual Specialization
--------------------------------

In Attempt 1 I had mentioned that we couldn't store the elements in arrays,
because `map`'s signature doesn't provide a `ClassTag` for us to create the
result array in. But that doesn't mean we can at least store the *initial* data
in a array. We'll create a new subtype of `Vec` that is specialized for
`Double` data. If a `Vec[A]` is constructed with an array of `Double`, we
can wrap a copy of the array in this specialized version of `Vec`.

```scala
sealed trait Vec[A] {
  def size: Int
  def apply(i: Int): A
  def map[B](f: A => B): Vec[B]
}

object Vec {
  def apply[A](elems: Array[A]): Vec[A] = elems match {
    case (elems: Array[Double]) => DoubleVec(java.util.Arrays.copyOf(elems, elems.length))
    case _ => GenericVec(elems.toVector)
}

private case class GenericVec[A](elems: Vector[A]) {
  def size: Int = elems.size
  def apply(i: Int): A = elems(i)
  def map[B](f: A => B): Vec[B] = GenericVec(elems.map(f))
}

private case class DoubleVec(elems: Array[Double]) {
  def size: Int = elems.size
  def apply(i: Int): Double = elems(i)
  def map[B](f: Double => B): Vec[B] = {
    val bldr = Vector.newBuilder[B]
    var i = 0
    while (i < elems.length) {
      bldr += f(elems(i))
      i += 1
    }
    GenericVec(bldr.result())
  }
}
```

You'll notice in our `Vec` constructor we are pattern matching on the type of
the array with `case (elems: Array[Double]) =>`. This works because arrays
are special on the JVM. An array's generic type is not erased and we have
access to it at runtime and, hence, can pattern match on it. If our constructor
had, instead, taken a `Vector[A]` argument then this would not be possible.

We hope this implementation will be faster, due to the better cache locality of
primitive arrays over `Vector`s of objects. This theory is easily proved with
our benchmarks. So, let's run them and see if we're getting closer!

```
> attempt2/benchmark:benchmark
...
[info] Benchmark                                       Mode  Samples       Score  Score error  Units
[info] r.VecMapBenchmark.squareDoubleArrayWithLoop    thrpt       20  891294.031    46443.884  ops/s
[info] r.VecMapBenchmark.squareDoubleArrayWithMap     thrpt       20   83802.721     3388.733  ops/s
[info] r.VecMapBenchmark.squareDoubleVec              thrpt       20  129949.254     7370.374  ops/s
```

An improvement! We're now less than 7x slower than the while loop. However,
there is an obvious draw back here - as soon as we `map` a `Vec`, we've lost
our specialized context, `DoubleVec`, and are back to a `GenericVec` again. So,
while our first `map` may be fast, any subsequent ones will be as slow as the
general case. But don't lose hope, we just need to figure out a way to ensure
that if our result is a `Vec[Double]` then `DoubleVec` will be returned.

Attempt 3: Reflection
---------------------

So, how can we ensure that all `Vec[Double]`s are `DoubleVec`s? Well, if we
inspect each of our elements in the result and verify that it is a `Double`,
then we can safely store the result in an `Array[Double]`, casting all the
elements to `Double`, then wrap that in a `DoubleVec`. We can even just create
a simple constructor that does these checks and casts for us and return the
result in the appropriate subtype of `Vec`.

```scala
...
object Vec {
  ...

  private[respec] def fromVector[A](vec: Vector[A]): Vec[A] = {
    if (vec.forall(_.isInstanceOf[Double])) {
      val elems = vec.asInstanceOf[Vector[Double]].toArray
      DoubleVec(elems).asInstanceOf[Vec[A]]
    } else {
      GenericVec(vec)
    }
  }
}
...
```

Our first `.asInstanceOf` shows up here; it won't be our last. We're doing a
full scan over our vector and verifying all elements have type `Double`, then
using `.asInstanceOf` to regain the lost type `A` (which we know can be
`Double`). Once we have this, then we can create an array and stuff this into a
`DoubleVec`. We can then use this method in `map` to construct our result from
a `Vector[B]`.

```scala
final case class DoubleVec(elems: Array[Double]) extends Vec[Double] {
  ...
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
```

We can even use `fromVector` in `GenericVec` too - this is required so that something
like `vec.map(_.toString).map(_.toDouble)` will work correctly and respecialize the
`Vec` to `DoubleVec`.

```scala
case class GenericVec[A](elems: Vector[A]) extends Vec[A] {
  ...
  def map[B](f: A => B): Vec[B] = Vec.fromVector(elems.map(f))
}
```

Clearly `fromVector` will cost us something. We're reading the data twice and constructing
a new array, on top of what we were doing before. Let's see how much it hurts.

```
> attempt3/benchmark:benchmark
...
[info] Benchmark                                       Mode  Samples       Score  Score error  Units
[info] r.VecMapBenchmark.squareDoubleArrayWithLoop    thrpt       20  859644.869    29617.942  ops/s
[info] r.VecMapBenchmark.squareDoubleArrayWithMap     thrpt       20   84213.447     3304.613  ops/s
[info] r.VecMapBenchmark.squareDoubleVec              thrpt       20   59952.755     4436.288  ops/s
```

Ouch. Those extra reads and array allocation/writes really hurt us. It doesn't
necessarily mean the strategy is bad though, but we have to figure out someway
to inline it with `map`'s while loop. We cannot rely on having time to verify
types and convert `Vector`s to `Array`s after the fact.

Attempt 4: Assume the Best, Prepare for the Worst
-------------------------------------------------

If we could just *assume* that our function returns `Double`s, then we could
just allocate the `Array[Double]` at the start, and start filling it up in our
while loop, rather than appending results to the `Vector` builder. Can we be so
optimistic? Sure - if we attempt to cast some `B` to a `Double` and we're wrong
we'll get a `ClassCastException`. That means we can wrap our while loop in a
`try`/`catch` block and, on failure, simply use our old `Vector` building loop.

A simple implementation would look like this:

```scala
final case class DoubleVec(elems: Array[Double]) extends Vec[Double] {
  ...
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
```

Ack! More `asInstanceOf`s! You can see we just optimistically assume the result
is a `Double` at the start and only revert to the deoptimized approach when
we're proven wrong. Also, note that we evaluate `f` twice the first time it
doesn't return a `Double`; we could fix this here, but we're not quite done yet
and we this will be less of an issue later, so we'll ignore it for now.
Anyways, let's give this a go. We're hoping that we'll at least get back up to
the speeds we saw in **attempt 2**, but now with the added guarantee that if
the result is a `Vec[Double]` we'll get back a `DoubleVec`.

```
> attempt4/benchmark:benchmark
...
[info] Benchmark                                       Mode  Samples       Score  Score error  Units
[info] r.VecMapBenchmark.squareDoubleArrayWithLoop    thrpt       20  913747.139    66282.558  ops/s
[info] r.VecMapBenchmark.squareDoubleArrayWithMap     thrpt       20   88184.863     5186.405  ops/s
[info] r.VecMapBenchmark.squareDoubleVec              thrpt       20  418544.050    24288.033  ops/s
```

Wow! We're now nearly within 2x slowdown over the while loop! Yeah, the code is
not a great example of idiomatic Scala, but it is *fast*. Writing to an array
optimistically means that we can avoid all the allocations and bad locality of
`Vector` when our result is, in fact, a `Vec[Double]`. Do we stop here? Nope.
Our goal was to get as close to `squareDoubleArrayWithLoop` as possible, and
there is still plenty of room.

Attempt 5: Exploiting @specialized
----------------------------------

The next sensible question to ask is *why is ours slower*? The key is really here:

```scala
  def map[B](f: Double => B): Vec[B] = {
    ...
      bs(i) = f(elems(i)).asInstanceOf[Double]
    ...
  }
```

We're using `f` as a generic function from `Double => B`, and only attempt to
cast the return value of `f` to a `Double` after the function application.
Since `B` is generic, we'll actually be getting back an `Object`, not a
primitive, since `B` is erased at runtime. Not only that, even though we *know*
that our input type is a `Double`, we're *still* boxing it prior to applying `f`.
The reason for this is that `Function1` doesn't specialize on `AnyRef`, only
`Int`, `Long`, `Float`, and `Double`. This means specialized variants of
`Function1`'s apply are only generated when both the argument and return type
are one of the 4 specialized types. A real shame. This means we're creating 2
boxes on each iteration - one to box the double on the way *into* `f` and another
that boxes the double on the way *out of* `f`. If we can get rid of this, perhaps
we can reach our goal.

In order to get Scala to use the specialized versions of `Function1`'s apply
(eg. `apply$mcDD$sp`, aka `Double => Double`) we need to convince the compiler
that both the input and output types are `Double` (or some combination of the
4 specialized types). What if we are super optimistic and not only assume that
`B` is a `Double`, but go so far as to cast `f` to a function `Double =>
Double`? Let's try it out!

First we'll define a function that isolates the behaviour we want to implement.

```scala
def ap[B](f: Double => B): Double = {
  val fDD = f.asInstanceOf[Double => Double]
  fDD(42D)
}
```

This will optimistically cast `f` as `Double => Double` and attempt to apply the
function to `42`. Let's take a look at the bytecode and see what this compiles
down to:

```
  public <B extends java/lang/Object> double ap(scala.Function1<java.lang.Object, B>);
    Code:
      0: aload_1
      1: ldc2_w        #15                 // double 42.0d
      4: invokeinterface #22,  3           // InterfaceMethod scala/Function1.apply$mcDD$sp:(D)D
      9: dreturn
```

You'll notice a few things. First, our cast is gone! Both `Double => B` and
`Double => Double` erase to the same type at runtime time, so the cast is
*purely* for the benefit of the Scala compiler. The 2nd is that we are calling
`apply$mcDD$sp` on `Function1` - this is what we were hoping for. This means we
are specializing on both the input type (the 1st `D`) and the return type (the
2nd `D`). We are no longer getting an `Object` back, but a `double` (primitive).

OK, so this is half the equation - the optimized case. The bigger question is
what happens if we're *wrong*? Well, let's find out.

```scala
scala> ap[String](_.toString)
java.lang.ClassCastException: java.lang.String cannot be cast to java.lang.Double
  at scala.runtime.BoxesRunTime.unboxToDouble(BoxesRunTime.java:118)
  at scala.Function1$class.apply$mcDD$sp(Function1.scala:39)
  at scala.runtime.AbstractFunction1.apply$mcDD$sp(AbstractFunction1.scala:12)
  at Ap$.ap(<console>:9)
  ... 43 elided
```

A `ClassCastException` - exactly what we were hoping for. Why is this? Since
`Function1` is specialized on the argument and return type, the interface
includes variations of `apply` for all combinations of specialization - in
our case, `apply$mcDD$sp`. Even if a function doesn't use primitives, it must
still implement these variations. Scala provides defaults for all versions in
`AbstractFunction1`, which simply delegate to a static version of the method on
`Function1$class`. The method looks like this:

```
  public static double apply$mcDD$sp(scala.Function1, double);
    Code:
       0: aload_0
       1: dload_1
       2: invokestatic  #32                 // Method scala/runtime/BoxesRunTime.boxToDouble:(D)Ljava/lang/Double;
       5: invokeinterface #38,  2           // InterfaceMethod scala/Function1.apply:(Ljava/lang/Object;)Ljava/lang/Object;
      10: invokestatic  #50                 // Method scala/runtime/BoxesRunTime.unboxToDouble:(Ljava/lang/Object;)D
      13: dreturn
```

It will box things up and call the generic version. It'll then attempt to unbox
the generic (`Object`) result as a `double`. Look familiar? It should - this is
exactly what we were already doing above. The key difference, of course, is that
now it is done for us when `f`'s return type isn't `Double` and, most importantly,
if `f`'s return type is `Double`, then we just get back the `double`, no round
trip through a box!

Alright, so let's exploit this new knowledge to get rid of the boxing on the return
side of `f`.

```scala
final case class DoubleVec(elems: Array[Double]) extends Vec[Double] {
  ...
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
```

We made a few other changes here, aside from the cast. Most notably, we don't
immediately assume the return type is `Double`, but rather run `f` once
generically, then we pattern match on the return type to see if it is a
`Double`. The main reason we do this here is to avoid throwing/catching
exceptions in the common case (when `B` isn't a `Double`), since they're
costly.  It is still possible the head is a `Double`, but some other values
aren't, but I don't believe that isn't a case worth optimizing for. The other
reason we pattern match on the head is to better support multiple
specializations.  In this article we only manually specialized on `Double`, but
if were to also support, say, manually specialized `IntVec` and `LongVec` along
with this, then this pattern-matching-on-the-head approach let's us dispatch to
each of these special cases 1 in spot.

The function now resembles something I'd find at the bottom of my compost bin,
but hopefully this horror pays us back in the benchmarks...

```
> attempt5/benchmark:benchmark
...
[info] Benchmark                                       Mode  Samples       Score  Score error  Units
[info] r.VecMapBenchmark.squareDoubleArrayWithLoop    thrpt       20  882646.295    45763.073  ops/s
[info] r.VecMapBenchmark.squareDoubleArrayWithMap     thrpt       20   85871.737     2158.022  ops/s
[info] r.VecMapBenchmark.squareDoubleVec              thrpt       20  892060.281    50342.168  ops/s
```

And there it is! Even with a completely generic `Vec[A]` trait, we are still
able to write a `map` that is as fast as while loop on a primitive array,
without requiring we litter our methods with `ClassTag`s and `@specialized`
annotations.

Conclusion
----------

We set out at the start of this article with the goal of implementing `Vec[A]` in such
a way that, for some primitive types, like `Double`, we get same performance
we'd expect from manually written loops over primitive arrays. The added
difficulty was that we weren't allowed to use `ClassTag`s and it had to work
outside of a specialized context. Our final attempt (5) hit the mark dead on!

Of course, there is a lot of code duplication, casts, reflection, and all sorts
of other terrible things. This kind of code is rarely worth the performance
benefit.  In Framian, this kind of code is largely contained in our `Column`
data structure. It's got a small API foot print, but it's used so ubiquitously
that it is worth optimizing.

[Framian]: https://github.com/pellucidanalytics/framian
[Project]: https://github.com/tixxit/optimistic-spec-article