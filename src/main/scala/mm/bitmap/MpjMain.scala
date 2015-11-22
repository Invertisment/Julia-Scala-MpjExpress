package mm.bitmap

import java.lang.Math._

import mm.bitmap.Main.DefaultPixelGeneratorSupplier
import mm.bitmap.gen.{PixelGenerator, Point, RealPixelGenerator}
import mpi.MPI

import scala.concurrent.ExecutionContext

/**
 * @author Martynas Maciulevičius.
 * @version 1.0 2015-10-14
 */
object MpjMain {
  def main(args: Array[String]): Unit = {

    println(args.foldLeft(new StringBuffer())(_.append(_).append(" ")))

    val split = args.splitAt(3)

    val parsed = Main.parseArgs(split._2)
    parsed
      .foreach(conf =>
        if (conf.runOverMpj)
          wrappedMain(split._1, Some(conf))
        else
          Main.run(Some(conf)))
  }

  def separateMpjArgs(args: Array[String]): Option[(Array[String], Array[String])] = {
    val temp = Array("--mpj", "-m")
      .map(getArgPos(_, args))
      .filter(_.isDefined)
      .map(_.get)
      .headOption
    temp.map(i => args.splitAt(i + 1))
  }

  def getArgPos(arg: String, args: Array[String]): Option[Int] = {
    Some(args.indexOf(arg)).filter(!_.equals(-1))
  }

  def wrappedMain(mpjArgs: Array[String], config: Option[Config]): Unit = {
    MPI.Init(mpjArgs)
    run(config)
    MPI.Finalize()
  }

  def run(config: Option[Config]): Unit = {
    Main.run(config, new MpjMasterGeneratorSupplier)
  }

  private class MpjMasterGeneratorSupplier extends PixelGeneratorSupplier {

    val size = MPI.COMM_WORLD.Size()
    val myRank = MPI.COMM_WORLD.Rank()
    val nodes = Stream.iterate[Int](1)(_ + 1).map(_ % size).toIterator

    override def createGenerator(config: Config)(implicit executionContext: ExecutionContext): RealPixelGenerator = {
      new RealPixelGenerator(Main.getOccurenceCounter(config)(executionContext))(executionContext) {

        override def generate(width: Int, height: Int, coordBounds: (Point, Point)): Option[Array[Int]] = {
          val wSize = MPI.COMM_WORLD.Size()
          val rank = MPI.COMM_WORLD.Rank()

          val workCount = width * height / wSize
          val output = Array.ofDim[Int](width * height) // TODO: optimise memory allocation on slave nodes :/

          val arr = splitInts(0, height, wSize, rank)
            .map((range: (Int, Int)) => {
              val subBounds = subset((0, height), reflectRange((0, height), range), coordBounds)
              println("doing@" + rank + ": " + range + " " + subBounds + " size: " +(width, range._2 - range._1))
              super.generate(width, range._2 - range._1, subBounds)
            })
            .getOrElse(None)
            .getOrElse(Array.ofDim[Int](0))

          // Copy the data to output array
          for (i <- arr.indices) {
            output(i) = arr(i)
          }

          // Send data
          MPI.COMM_WORLD.Gather(output, 0, workCount, MPI.INT, output, 0, workCount, MPI.INT, 0)

          // Stop minions
          if (rank != 0) {
            println("stopping instance: " + rank)
            MPI.COMM_WORLD.Barrier()
            return None
          }

          MPI.COMM_WORLD.Barrier()

          println("Survivor: " + rank)

          Some(output)
        }
      }
    }

  }

  def subset(fromRange: (Int, Int), toRange: (Int, Int), coordBounds: (Point, Point)): (Point, Point) = {

    val y = coordBounds._1.maxCoord(coordBounds._2).y

    val currentScale = max(fromRange._1, fromRange._2) - min(fromRange._1, fromRange._2)
    val minValue = coordBounds._1.minCoord(coordBounds._2).y

    (
      Point(
        coordBounds._1.minCoord(coordBounds._2).x,
        scale(
          currentScale,
          min(toRange._1, toRange._2),
          y,
          minValue)),
      Point(
        coordBounds._1.maxCoord(coordBounds._2).x,
        scale(
          currentScale,
          max(toRange._1, toRange._2),
          y,
          minValue)))
  }

  def reflectRange(fromRange: (Int, Int), toRange: (Int, Int)): (Int, Int) = {
    val toMin = min(toRange._1, toRange._2)
    val doneMax = max(fromRange._1, fromRange._2) - toMin + min(fromRange._1, fromRange._2)
    (doneMax - max(toRange._1, toRange._2) + toMin, doneMax)
  }

  def scale(currentScale: Int, toScale: Int, value: Double, minValue: Double): Double = {
    (value - minValue) / currentScale * toScale + minValue
  }

  def splitInts(from: Int, to: Int, workerCount: Int, myIndex: Int): Option[(Int, Int)] = {
    // TODO: data will be malformed if base < workerCount and expected result is integer
    // Math.floor will not save from this error
    if (myIndex >= workerCount)
      return None
    val base = to - from
    val step = base / workerCount
    val start = from + myIndex * step
    val end = min(start + step, to)
    Some((start, end))
  }

  def getSlavePixelGen(config: Config): PixelGenerator =
    new DefaultPixelGeneratorSupplier().createGenerator(config)(ExecutionContext.Implicits.global)

}
