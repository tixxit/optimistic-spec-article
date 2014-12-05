package respec

import scala.util.Random

import org.openjdk.jmh.annotations.{ Benchmark, Scope, State }

class VecMapBenchmark {

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

  @Benchmark
  def squareDoubleArrayWithMap(data: MapData) =
    data.doubleData.map(x => x * x)

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
