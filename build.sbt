organization in ThisBuild := "com.alexknvl"

scapegoatVersion in ThisBuild := "1.3.8"

unusedCompileDependenciesFilter in ThisBuild :=
  (moduleFilter()
    - moduleFilter("com.sksamuel.scapegoat", "scalac-scapegoat-plugin")
    - moduleFilter("com.github.ghik", "silencer-lib")
    - moduleFilter("com.lihaoyi", "acyclic"))

scalaVersion in Global := "2.12.8"

updateOptions in ThisBuild := updateOptions.value.withCachedResolution(true)

// http://www.scalatest.org/user_guide/using_the_runner#configuringReporters
testOptions in Test in ThisBuild += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")

// https://github.com/rickynils/scalacheck/blob/master/doc/UserGuide.md#test-execution
testOptions in Test in ThisBuild += Tests.Argument(TestFrameworks.ScalaCheck, "-verbosity", "1")

javacOptions in ThisBuild ++= List(
  "-source", "1.8",
  "-target", "1.8"
)

scalacOptions in ThisBuild in compile ++= List(
  "-Ywarn-value-discard"
)

scalacOptions in ThisBuild ++= List(
  "-deprecation",
  "-encoding", "UTF-8",
  "-explaintypes",
  "-Yrangepos",
  "-feature",
  "-Xfuture",
  "-Ypartial-unification",
  "-language:higherKinds",
  "-language:existentials",
  "-unchecked",
  "-Yno-adapted-args",
  "-opt-warnings",
  "-Xlint:_,-type-parameter-shadow,-adapted-args",
  "-Xsource:2.13",
   "-Ywarn-extra-implicit",
  "-Ywarn-inaccessible",
  "-Ywarn-infer-any",
  "-Ywarn-nullary-override",
  "-Ywarn-nullary-unit",
  // "-Ywarn-numeric-widen",
  // "-Ywarn-unused:_,-imports",
  // "-Ywarn-value-discard",
  "-Ywarn-unused-import",
  // "-opt:l:inline",
  // "-opt-inline-from:<source>",
  "-Xmax-classfile-name", "254"
)

// Enforce exhaustive matches.
libraryDependencies in ThisBuild ++= Seq(
  compilerPlugin(Dependencies.Plugin.nonExhaustiveMatchError))

// Better monadic for comprehensions.
libraryDependencies in ThisBuild ++= Seq(
  compilerPlugin(Dependencies.Plugin.betterMonadicFor))

// Type lambdas.
libraryDependencies in ThisBuild ++= Seq(
  compilerPlugin(Dependencies.Plugin.kindProjector))

// Better implicit error explanations.
libraryDependencies in ThisBuild ++= Seq(
  compilerPlugin(Dependencies.Plugin.splain))

// @silence annotation
libraryDependencies in ThisBuild ++= Seq(
  compilerPlugin(Dependencies.Plugin.silencerPlugin),
  Dependencies.Plugin.silencer)

// Always fork when running standalone or running tests.
fork in run in Global := true
fork in Test in Global := true

publishArtifact in (Compile, packageSrc) in ThisBuild := false
publishArtifact in (Compile, packageDoc) in ThisBuild := false

autoAPIMappings in ThisBuild := true

lazy val root = (project in file("."))
  .aggregate(libFastCodec)
  .settings(skip in publish := true)

lazy val libFastCodec = project.in(file("fastcodec"))
  .settings(name := "fastcodec")
  .settings(Settings.common(enableWarts = Disable, acyclic = Disable, fatalWarnings = Disable))
  .settings(
    libraryDependencies ++= List(
      Dependencies.scalaTest  % Test,
      Dependencies.scalacheck % Test,
      Dependencies.zio,
      Dependencies.zioStream,
      Dependencies.asm
    )
  )