import Dependencies._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

import scalariform.formatter.preferences._

lazy val commonSettings = Seq(
  organization := "de.envisia",
  scalaVersion := "2.12.2",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.11"),
  scalacOptions in(Compile, doc) ++= Seq(
    "-target:jvm-1.8",
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation"
  ),
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-o"),
  publishMavenStyle in ThisBuild := true,
  pomIncludeRepository in ThisBuild := { _ => false },
  publishTo in ThisBuild := Some("Artifactory Realm" at "https://maven.envisia.de/open")
)

val formattingSettings = Seq(
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(SpacesAroundMultiImports, true)
      .setPreference(SpaceInsideParentheses, false)
      .setPreference(DanglingCloseParenthesis, Preserve)
      .setPreference(PreserveSpaceBeforeArguments, true)
      .setPreference(DoubleIndentClassDeclaration, true)
)

lazy val `akka-pg` = (project in file("."))
    .settings(commonSettings)
    .settings(formattingSettings)
    .settings(
      libraryDependencies ++= Seq(
        slf4j,
        akkaStream,
        scalaTest % Test
      )
    )



// To sync with Maven central, you need to supply the following information:
pomExtra in Global := {
  <url>https://github.com/schmitch/akka-pg</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>https://www.apache.org/licenses/LICENSE-2.0</url>
        </license>
      </licenses>
      <scm>
        <connection>scm:git@github.com:schmitch/akka-pg.git</connection>
        <developerConnection>scm:git:git@github.com:schmitch/akka-pg.git</developerConnection>
        <url>github.com/schmitch/akka-pg</url>
      </scm>
      <developers>
        <developer>
          <id>schmitch</id>
          <name>Christian Schmitt</name>
          <url>https://git.envisia.de/schmitch</url>
        </developer>
      </developers>
}

releaseCrossBuild := true

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
  setNextVersion,
  commitNextVersion,
  pushChanges
)