name := """akka-pg"""

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.11.8"

crossScalaVersions := Seq(scalaVersion.value/*, "2.12.1"*/)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.4.14",
  "org.scalatest" %% "scalatest" % "3.0.0" % Test
)