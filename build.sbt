import Dependencies._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import ReleaseTransformations._

import scalariform.formatter.preferences._

lazy val commonSettings = Seq(
  organization := "de.envisia.database",
  scalaVersion := "2.12.8",
  crossScalaVersions := Seq("2.12.8", "2.13.0"),
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
  publishTo in ThisBuild := Some("envisia-internal" at "https://nexus.envisia.io/repository/internal/")
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
        "ch.qos.logback" % "logback-classic" % "1.2.3" % "test",
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
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runClean,                               // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  releaseStepCommandAndRemaining("+publishSigned"), //publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
  setNextVersion,                         // : ReleaseStep
  commitNextVersion,                      // : ReleaseStep
  pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
)
