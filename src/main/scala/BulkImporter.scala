package d_m

import scala.tools.nsc
import nsc.Global
import nsc.Phase
import nsc.plugins.Plugin
import nsc.plugins.PluginComponent
import nsc.transform.Transform
import nsc.transform.InfoTransform
import nsc.transform.TypingTransformers
import nsc.symtab.Flags._
import nsc.ast.TreeDSL
import nsc.typechecker

import scala.collection.mutable

/**
 * Used to share state between the two phases. The state in question is a
 * mapping from "paths" (the fully-qualified names of objects annotated with
 * @Exports) to "imports" (a list of trees of imports).
 */
class State() {
  // ugly, but trying to figure out how to share trees between phases without
  // angering the cake monster was too much work. sigh.
  var exporters = mutable.Map.empty[String, List[Any]]
}

class BulkImporter(val global:Global) extends Plugin {
  val name = "bulk-importer"
  val description = "allows bulk import from @Exporter objects"

  val state = new State()

  val components = List(
    new BulkImportDetector(this, state, global),
    new BulkImportRewriter(this, state, global)
  )
}

/**
 * This particular phase search for objects annotated with @Exporter, makes
 * sure that the object in question only contains import statements, and then
 * stores the object's fully-qualified name and imports together in the
 * shared state.
 */
class BulkImportDetector(plugin:Plugin, val state:State, val global:Global)
extends PluginComponent with Transform with TypingTransformers with TreeDSL {

  import global._

  val runsAfter = "parser" :: Nil
  val phaseName = "bulk-exporter"

  // some names we're going to be using
  val _Exporter = newTermName("Exporter")
  val _init = newTermName("<init>")

  def newTransformer(unit:CompilationUnit) = new MyTransformer(unit)

  class MyTransformer(unit:CompilationUnit) extends TypingTransformer(unit) {

    /**
     * Given a list of trees (which should be annotations) this method will
     * determine whether any of the annotations are @Exporter.
     */
    def isExporter(as:List[Tree]): Boolean = as match {
      case Nil => false
      case Apply(Select(New(_Exporter), _init), Nil) :: t => true
      case _ :: t => isExporter(t)
    }

    /**
     * This method search for @Exporter objects, returning a list of results.
     * Each result is a tuple containing the "path" (the object's
     * fully-qualified name) and "imports" (a list of trees of imports).
     *
     * The list of tuples will be added to our mutable state map.
     */
    def findExporters(t:Tree, names:List[String]):List[(String, List[Tree])] = t match {
      case PackageDef(Ident(name), body) =>
        body.flatMap(t => findExporters(t, name.toString :: names))

      case ModuleDef(Modifiers(_, _, anns, _), name, Template(_, _, _ :: impl)) =>
        if (isExporter(anns)) {
          val path = (name.toString :: names).reverse.mkString(".")
          impl.foreach {
            case t:Import => {}
            case t => sys.error("arggh: %s" format t)
          }
          List((path, impl))
        } else {
          Nil
        }

      case t =>
        Nil
    }

    /**
     * We basically want to search the top-level package to see if we have any
     * @Exporter objects. Note that we don't currently make any attempt to
     * consider objects in "exotic" places (e.g. inside other objects).
     */
    override def transform(tree: Tree): Tree = tree match {
      case t:PackageDef =>
        state.exporters ++= findExporters(t, Nil)
        t
      case t =>
        t
    }
  }
}

/**
 * Using our mapping of @Exporter objects from the previous phase, this phase
 * will attempt to find wildcard imports from exporter objects, and replace
 * those with whatever imports the object contained.
 */
class BulkImportRewriter(plugin:Plugin, val state:State, val global:Global)
extends PluginComponent with Transform with TypingTransformers with TreeDSL {
  import global._

  val runsAfter = "parser" :: Nil
  val phaseName = "bulk-importer"

  def newTransformer(unit:CompilationUnit) = new MyTransformer(unit)

  class MyTransformer(unit:CompilationUnit) extends TypingTransformer(unit) {

    /**
     * Given a tree pulled from an Import, this method will return the full
     * package being imported from.
     */
    def parseImport(tree:Tree, names:List[String]): String = tree match {
      case Select(t, name) => parseImport(t, name.toString :: names)
      case Ident(name) => (name.toString :: names).mkString(".")
      case t => sys.error("unexpected tree: %s" format t)
    }

    /**
     * Given a path (fully-qualified package), this method will either:
     *  1. return a list of import trees (if the path was @Exported)
     *  2. return nothing (otherwise)
     */
    def getImports(path:String): Option[List[Tree]] = {
      state.exporters.get(path).map(_.asInstanceOf[List[Tree]]) //ugh
    }

    /**
     * This method attempts to recursively resolve imports to check for
     * wildcard imports from @Exporter objects. It will return a list of trees
     * to be injected into the parent tree (this is because one original
     * import tree will map to zero-or-more exported import trees).
     */
    def resolveBulkImports(t:Tree): List[Tree] = t match {
      case Import(pkg, ImportSelector(nme.WILDCARD, _, _, _) :: Nil) =>
        getImports(parseImport(pkg, Nil)).getOrElse(transform(t) :: Nil)

      case _ =>
        List(transform(t))
    }

    /**
     * Given a list of trees, return a new list of trees, replacing any
     * wilcard imports from @Exporter objects with the desired import trees.
     */
    def resolve(ts:List[Tree]) = ts.flatMap(resolveBulkImports)

    /**
     * Transform the input tree according to the phase. Note that we are only
     * going to look for imports in the kinds of trees that can have them.
     *
     * TODO: Figure out how to leverage Traversers and/or Transformers to do
     * this automatically. It's probably bad to hardcode this list of trees
     * here but I can't figure out a better way.
     */
    override def transform(tree: Tree): Tree = tree match {
      case PackageDef(p, stats) => PackageDef(p, resolve(stats))
      case Block(stats, e) => Block(resolve(stats), e)
      case Template(p, s, body) => Template(p, s, resolve(body))
      case t => super.transform(t)
    }
  }
}
