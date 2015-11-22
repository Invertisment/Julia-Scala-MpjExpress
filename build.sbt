name := "scalaMpjTest"

version := "1.0"

scalaVersion := "2.11.7"

baseAssemblySettings

assemblyJarName in assembly := "Fractals.jar"

lazy val root = Project(
  id = "scalaMpjTest",
  base = file("."))
  .dependsOn(core)
  .aggregate(core)

lazy val core = Project(id = "core",
  base = file("1uzd"))

lazy val printClasspath = taskKey[Unit]("Dump classpath")

printClasspath :=
  (fullClasspath in Runtime value)
    .foreach(e => println(e.data))


