package exports

import d_m.Exporter

@Exporter object Qux {
  import Predef.{any2stringadd => _, _}
  import scala.math.pow
}

@Exporter object Bux {
  import java.lang.Math.sqrt
}
