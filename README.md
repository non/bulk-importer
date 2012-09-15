## Bulk Importer

### Overview

Do you find yourself writing tons of boilerplate imports at the top of every
Scala file? Masking stuff from Predef, enabling language features, importing
vital annotations like tailrec, it's all in a day's work.

This plugin aims to fix this by allowing the user to import a single alias
which is translated at compile time into all the desired imports.

### Building the plugin

Using SBT, you should just be able to run `package` to build the plugin. You
can run the (meager) tests using `test`, but please be aware that repeated
test runs will fail, so make sure to run `clean` between them. (For more
information about these test failures, see the "Disclaimers" section.)

### How to use it

First, you'll want to create a file for your bulk exports (e.g.
`exports.scala`). Here's a set of exports I might want to use:

```scala
package exports

import d_m.Exporter

@Exporter object Bulk {
    import Predef.{any2stringadd => _, _}
    import scala.annotation.tailrec
    import scala.{specialized => spec}
    import scala.math._
}
```

Once that's done, I can create a file (e.g. `demo.scala`) which uses the
exports, a few I call "bulk importing". Here's an example:

```scala
package demo

// in this example, this import provides @tailrec, abs, and sqrt.
import exports.Bulk._

object Demo {
  @tailrec def gcd(a:Int, b:Int): Int = if (b == 0) abs(a) else gcd(b, a % b)

  def main(args:Array[String]) {
    println("the square root of 529 is %s" format sqrt(529))
  }
}
```

The last crucial step is to compile using the compiler plugin, and to make
sure to always compile `exports.scala` as well as `demo.scala`. If you fail to
compile the exports file, your exports will not be found. Here's an example
command line to use:

```
scalac -Xplugin:bulk-importer_2.9.2-0.1.jar demo.scala exports.scala
```

You'll need to make sure the plugin can be found on your classpath.

### How it works

During compilation, any object annotated with `@Exporter` is scanned for
imports, which are stored together with the object's path. In a later phase,
imports are scanned to see if they match one of these exporter objects. If so,
the "bulk imports" are substituted for the exporter object.

Observant readers will note that unless `exports.scala` is being scanned on the
current compilation run the exporter objects won't be found. That is definitely
a major limiation of the approach.

### Disclaimers

This is not guaranteed to work. In fact, it will definitely break if you use
SBT, an IDE, or any build tool which will try to avoid unnecessary
recompilation.

In the future, I'll try to use some other mechanism to encode the imports.
Unfortunately, none of the options look that attractive. We could pickle the
imports somehow, but that means that the plugin would need to somehow scan the
entire classpath looking for these things. We could put them in some kind of
external configuration file, but this is obviously pretty ugly, error-prone,
and clunky. If you have ideas please let me know!

It would be trivial to build a plugin with a hardcoded set of imports, so if
anyone wanting a more robust version of this plugin for actual use should
consider just building a hardcoded version (or asking for one nicely).

Beyond the partial compilation issues, the machinery is somewhat fragile. I'm
going to try to work on implementing better error messages and warnings but for
now I would encourage you to use top-level objects that only contain imports.

### Copyright and License

All code is available to you under the MIT license, available at
http://opensource.org/licenses/mit-license.php. 

Copyright Erik Osheim, 2012.
