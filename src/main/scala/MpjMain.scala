import mpi.MPI

/**
 * @author Martynas Maciulevičius.
 * @version 1.0 2015-10-14
 */
object MpjMain {
  def main(args: Array[String]): Unit = {
    println("args: " + args.foldLeft(new StringBuffer())(_.append(", ").append(_)))
    MPI.Init(args)
    MPI.Finalize()
  }
}
