import exports.Qux._

class Bomb

object Test {
  def test(n:Int) = pow(n, 2)

  //// this should explode if uncommented
  //def exploder = (new Bomb) + "explodes"

  def main(args:Array[String]) {
    import exports.Bux._
    println(test(9))
    println(sqrt(25))
  }
}
