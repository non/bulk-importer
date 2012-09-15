package bulk

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

class BulkImporter(val global:Global) extends Plugin {
  val name = "bulk-importer"
  val description = "allows bulk import from @Exporter objects"
  val components = List(new BulkImportRewriter(this, global))
}

class BulkImportRewriter(plugin:Plugin, val global:Global)
extends PluginComponent with Transform with TypingTransformers with TreeDSL {
  import global._

  val runsAfter = "parser" :: Nil
  val phaseName = "bulk-importer"

  val exporters = mutable.Map.empty[String, List[Tree]]

  loadJsonConfig()

  def loadJsonConfig() {
    import scala.util.parsing.json.JSON

    val maybeJson = loadJsonString() flatMap (JSON.parseFull) collect {
      case stmts:Map[_, _] => stmts.asInstanceOf[Map[String, Any]]
    }

    maybeJson match {
      case Some(json) => parseJsonConfig(json)
      case None => warning("bulk/exports.json could not be parsed")
    }
  }

  def loadJsonString(): Option[String] = {
    val cl = getClass.getClassLoader
    val st = cl.getResourceAsStream("bulk/exports.json")
    if (st == null) return None

    try {
      val br = new java.io.BufferedReader(new java.io.InputStreamReader(st))
      val sb = new StringBuilder
      var line = br.readLine()
      while (line != null) {
        sb.append(line)
        line = br.readLine
      }
      Some(sb.toString)
    } catch {
      case e:Exception => None
    }
  }

  def targetToImportSelector(t:Any) = t match {
    case "_" =>
      ImportSelector(nme.WILDCARD, -1, nme.WILDCARD, -1)

    case (a:String) =>
      val name = newTermName(a)
      ImportSelector(name, -1, name, -1)

    case (a:String) :: "_" :: Nil =>
      ImportSelector(newTermName(a), -1, nme.WILDCARD, -1)

    case (a:String) :: (b:String) :: Nil =>
      ImportSelector(newTermName(a), -1, newTermName(b), -1)

    case x =>
      sys.error("couldn't handle %s" format x)
  }

  def targetsToImportSelectors(ts:List[Any]) = ts.map(targetToImportSelector)

  def pkgToImport(pkg:String, selectors:List[ImportSelector]) = {
    val p = pkg.split("\\.").toList match {
      case h :: t => t.foldLeft(Ident(h):Tree)(Select(_, _))
      case Nil => sys.error("invalid pkg: %s" format pkg)
    }
    Import(p, selectors)
  }

  def translateToTree(v:Any): List[Tree] = v match {
    case (pkg:String) :: (targets:List[_]) :: Nil =>
      pkgToImport(pkg, targetsToImportSelectors(targets)) :: Nil
    case _ =>
      Nil
  }

  def translateToTrees(v:Any): List[Tree] = v match {
    case imports:List[_] => imports.asInstanceOf[List[Any]].flatMap(translateToTree)
    case _ => Nil
  }

  def parseJsonConfig(stmts:Map[String, Any]) {
    stmts.foreach {
      case (k, v) => exporters("bulk." + k) = translateToTrees(v)
    }
  }

  def newTransformer(unit:CompilationUnit) = new MyTransformer(unit)

  class MyTransformer(unit:CompilationUnit) extends TypingTransformer(unit) {

    /**
     * Given a tree pulled from an Import, this method will return the full
     * package being imported from.
     */
    def parseImport(tree:Tree, names:List[String]): String = tree match {
      case Select(t, name) => parseImport(t, name.toString :: names)
      case Ident(name) => (name.toString :: names).mkString(".")
      case t => { error("unexpected tree: %s" format t); null }
    }

    /**
     * Given a path (fully-qualified package), this method will either:
     *  1. return a list of import trees (for paths in bulk/exports.json)
     *  2. return nothing (otherwise)
     */
    def getImports(path:String): Option[List[Tree]] = {
      exporters.get(path).map(_.asInstanceOf[List[Tree]]) //ugh
    }

    /**
     * This method attempts to recursively resolve imports to check for
     * wildcard imports from exporter objects. It will return a list of trees
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
     * wilcard imports from exporter objects with the desired import trees.
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
