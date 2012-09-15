import bulk.foo._
import bulk.bar._

class Bomb

object Test {
  def test1(n:Int) = pow(n, 2)
  def test2(n:Double) = ceiling(n)
  def test3(n:Int) = sqrt(n)

  //// this should explode if uncommented
  //def exploder = (new Bomb) + "explodes"

  def main(args:Array[String]) {
    println(test1(9))
    println(test2(33.3))
    println(test3(25))
  }
}
