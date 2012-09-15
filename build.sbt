name := "bulk-importer"

version := "0.1"

scalaVersion := "2.9.2"

scalacOptions += "-unchecked"

libraryDependencies <++= (scalaVersion) {
  v => Seq("org.scala-lang" % "scala-compiler" % v)
}

scalacOptions in console in Compile <+= (packageBin in Compile) map {
  pluginJar => "-Xplugin:" + pluginJar
}

scalacOptions in Test <+= (packageBin in Compile) map {
  pluginJar => "-Xplugin:" + pluginJar
}
