scalaVersion in ThisBuild := "2.11.4"

scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature", "-unchecked", "-optimize")

lazy val root = project.
  in(file(".")).
  enablePlugins(BenchmarkPlugin)

lazy val attempt1 = project.
  in(file("attempt1")).
  enablePlugins(BenchmarkPlugin)

lazy val attempt2 = project.
  in(file("attempt2")).
  enablePlugins(BenchmarkPlugin)

lazy val attempt3 = project.
  in(file("attempt3")).
  enablePlugins(BenchmarkPlugin)

lazy val attempt4 = project.
  in(file("attempt4")).
  enablePlugins(BenchmarkPlugin)

lazy val attempt5 = project.
  in(file("attempt5")).
  enablePlugins(BenchmarkPlugin)

lazy val attempt6 = project.
  in(file("attempt6")).
  enablePlugins(BenchmarkPlugin).
  settings(
    resolvers += Resolver.sonatypeRepo("snapshots"),
    libraryDependencies += "org.scala-miniboxing.plugins" %% "miniboxing-runtime" % "0.4-SNAPSHOT" changing(),
    addCompilerPlugin("org.scala-miniboxing.plugins" %% "miniboxing-plugin" % "0.4-SNAPSHOT" changing()),
    scalacOptions ++= Seq("-P:minibox:warn", "-P:minibox:Yrewire-functionX-application")
  )
