## Bulk Importer

### Overview

Do you find yourself writing tons of boilerplate imports at the top of every
Scala file? Masking stuff from Predef, enabling language features, importing
vital annotations like tailrec, it's all in a day's work.

This plugin aims to fix this by allowing the user to import a single alias
which is translated at compile time into all the desired imports.

### Building the plugin

To use this plugin, you'll need to build your own plugin jar with a customized
list of exporter packages (virtual packages which provide zero or more imports
each).

The first step is to edit `src/main/resources/bulk/exports.json`. This will be
covered in more detail in the following section.

Next, from SBT you should run `package` to build the plugin.

Finally, you can run the tests using `test`. Note that the test compilation is
the real indicator of success--if the test compiles, then things are working
(presumably).

### Configuring the plugin

The bulk-importer plugin loads a configuration file from the plugin jar in
order to determine how to behave. The file is located at
`src/main/resources/bulk/exports.json` (and will be located at
`bulk/exports.json` in the plugin jar).

The format is relatively simple: there is a top-level object, whose keys
correspond to exporters (virtual packages). Each value is a list containing a
base package, and then one-or-more import selectors.

For instance, let's say you want `import bulk.math` to do the following:

```scala
import Predef.{any2stringadd => _, _}
import scala.collection.mutable
import scala.math.{ceil, floor, round}
import scala.annotation.tailrec
```

You'd want to use the following JSON file:

```javascript
{
  "math": [
    ["Predef", [["any2stringadd", "_"], "_"]],
    ["scala.collection", ["_"]],
    ["scala.math", ["ceil", "floor", "round", "sqrt"]],
    ["scala.annotation", ["tailrec"]]
  ]
}
```

If you wanted to create multiple bulk imports, you would create multiple
top-level keys, each of which would have its own internal structure. Note that
`bulk` is the implied top-level package, so if you wanted to use
`bulk.foo.bar` in your Scala code you'd want to use `foo.bar` in the JSON.

The structure of the configuration file is relatively rigid--the above example
demonstrates pretty much every available kind of selector.

### Using the plugin

After you've created your own compiler plugin jar, using it is relatively
easy. Here's an example using the previous configuration:

```scala
package demo

// note that this *must* be a wildcard import from bulk.math
import bulk.math._

object Demo {
  @tailrec def gcd(a:Int, b:Int): Int =
    if (b == 0) abs(a) else gcd(b, a % b)

  def main(args:Array[String]) {
    println("the square root of 529 is %s" format sqrt(529))
  }
}
```

The last crucial step is to compile using the compiler plugin. Here's an
example command line to use:

```
scalac -Xplugin:path/to/bulk-importer_2.9.2-0.1.jar demo.scala
```

If you use SBT, you can edit your `build.sbt` file to include the compiler plugin:

```
scalacOptions += "-Xplugin:path/to/bulk-importer_2.9.2-0.1.jar"
```

### How it works

When the compiler plugin's phase starts up, it requests the
`bulk/exports.json` file from the classloader. It parses that file and
determines which exporters  exist and what their effects should be.

Then during compilation, all imports are scanned, and any wildcard imports
from an exporter are rewritten to use the bulk imports instead. Note that
bulk-importer doesn't worry about whether the underlying imports are valid or
not. If there are problems, scalac will catch them in a later phase.

### Future work

There are some `sys.error` cases in the plugin which should really be
rewritten. Also, it would be nice to give specific warnings when we can detect
that the user is doing something wrong (e.g. non-wildcard imports from
exporters) or when the JSON file is malformed.

It would be really nice if the configuration didn't have to be built into the
plugin, but I couldn't easily think of a robust way for the user to supply
this kind of information to the plugin. Ideas welcome!

### Disclaimers

This is not guaranteed to work. It's been tested a bit but it's still alpha
quality code. If anyone is hoping to use this on their own codebase or at work
I'd strongly suggest contributing some more tests, and/or reviewing the
implementation.

Obviously the *right* way to do a feature like this would be to modify scalac.
This plugin is somewhat of a stopgap, as well as a way for users on 2.9.x and
2.10.x to get this functionality right now.

### Copyright and License

All code is available to you under the MIT license, available at
http://opensource.org/licenses/mit-license.php. 

Copyright Erik Osheim, 2012.
