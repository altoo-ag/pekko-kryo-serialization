//no support for sbt 2 available in the osgi plugin
//also pekko not yet using sbt 2.0 see https://github.com/apache/pekko/blob/main/project/plugins.sbt
//addSbtPlugin("com.github.sbt" % "sbt-osgi" % "0.10.0")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.5.0")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
