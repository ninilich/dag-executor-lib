import sbt.Keys.libraryDependencies

name := "dag-executor"
organization := "org.ninilich"
homepage := Some(url("https://github.com/ninilich/dag-executor-lib"))

scalaVersion := "2.12.15"
Test / parallelExecution := false // need to prevent conflicts using SparkSession in tests

// This configuration enables each run command to execute in a separate process,
// effectively resetting the JVM state between executions.
Compile / fork := true

// In SBT: switching off the [info] prefixes in logs:
outputStrategy := Some(StdoutOutput)

// for publishing
publishMavenStyle := true
pomIncludeRepository := { _ => false }
publishTo := Some("GitHub ninilich Apache Maven Packages" at "https://maven.pkg.github.com/ninilich/PACKAGE")
credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "USERNAME",
  System.getenv("GITHUB_TOKEN")
)


// libraryDependencies both for server and local run
libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.5.12" % "provided",
  "org.apache.spark" %% "spark-core" % "3.5.0" % "provided",
  "org.apache.spark" %% "spark-sql" % "3.5.0" % "provided",
  // for tests
  "org.scalatest" %% "scalatest" % "3.2.19" % "test"
)

// uses compile classpath for the run task, including "provided" jar on the local machine
Compile / run := Defaults
  .runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner)
  .evaluated
