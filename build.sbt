scalaVersion := "2.11.4"

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
