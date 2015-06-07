scalaVersion in ThisBuild := "2.11.6"

scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature", "-unchecked", "-optimize")

javaOptions in run ++= Seq("-Xms2g", "-Xmx2g", "-Xss4m",
			   "-XX:+CMSClassUnloadingEnabled",
			   "-XX:MaxPermSize=512M",
			   "-XX:ReservedCodeCacheSize=256m", "-XX:PermSize=256m",
			   "-XX:+TieredCompilation", "-XX:+UseNUMA")

lazy val root = project.
  in(file(".")).
  enablePlugins(BenchmarkPlugin).
  settings(
    name := "respecialization-root"
  )

lazy val attempt1 = project.
  in(file("attempt1")).
  enablePlugins(BenchmarkPlugin).
  settings(
    name := "respecialization-attempt1"
  )

lazy val attempt2 = project.
  in(file("attempt2")).
  enablePlugins(BenchmarkPlugin).
  settings(
    name := "respecialization-attempt2"
  )

lazy val attempt3 = project.
  in(file("attempt3")).
  enablePlugins(BenchmarkPlugin).
  settings(
    name := "respecialization-attempt3"
  )

lazy val attempt4 = project.
  in(file("attempt4")).
  enablePlugins(BenchmarkPlugin).
  settings(
    name := "respecialization-attempt4"
  )

lazy val attempt5 = project.
  in(file("attempt5")).
  enablePlugins(BenchmarkPlugin).
  settings(
    name := "respecialization-attempt5"
  )

lazy val attempt6 = project.
  in(file("attempt6")).
  enablePlugins(BenchmarkPlugin).
  settings(
    name := "respecialization-attempt6",
    libraryDependencies += "org.scala-miniboxing.plugins" %% "miniboxing-runtime" % "0.4-M4",
    addCompilerPlugin("org.scala-miniboxing.plugins" %% "miniboxing-plugin" % "0.4-M4"),
    scalacOptions ++= Seq("-P:minibox:mark-all")
  )
