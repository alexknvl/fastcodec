import sbt.Keys._
import sbt._
import wartremover.WartRemover.autoImport.Wart

sealed trait FlagValue
final case class Enable(compileOnly: Boolean) extends FlagValue
final case object Disable extends FlagValue

object Settings {
  def setting[A](compileOnly: Boolean, setting: SettingKey[A]): SettingKey[A] =
    if (compileOnly) setting in (Compile, compile) else setting
  def setting[A](compileOnly: Boolean, setting: TaskKey[A]): TaskKey[A] =
    if (compileOnly) setting in (Compile, compile) else setting

  def forceAcyclicity(compileOnly: Boolean) = List(
    setting(compileOnly, libraryDependencies) +=
      "com.lihaoyi" %% "acyclic" % "0.1.9" % "provided",
    addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.9"),
    setting(compileOnly, scalacOptions) += "-P:acyclic:force"
  )

  val warts = List(
    Wart.AnyVal,
    Wart.ArrayEquals,
    Wart.AsInstanceOf,
    Wart.EitherProjectionPartial,
    Wart.Enumeration,
    Wart.ExplicitImplicitTypes,
    Wart.FinalCaseClass,
    Wart.FinalVal,
    Wart.ImplicitConversion,
    Wart.IsInstanceOf,
    Wart.JavaSerializable,
    Wart.LeakingSealed,
    Wart.MutableDataStructures,
    Wart.NonUnitStatements,
    Wart.Null, // orNull and nulls are useful for exceptions...
    Wart.OptionPartial,
    Wart.Product,
    Wart.Return,
    Wart.Serializable,
    Wart.StringPlusAny,
    Wart.Throw,
    Wart.TraversableOps,
    Wart.TryPartial,
    Wart.Var,
    Wart.While)

  def forceFatalWarnings(compileOnly: Boolean) =
    if (compileOnly) List(scalacOptions in (Compile, compile) += "-Xfatal-warnings")
    else List(scalacOptions += "-Xfatal-warnings")

  def common
  (acyclic: FlagValue = Enable(false),
   fatalWarnings: FlagValue = Enable(false),
   enableWarts: FlagValue = Enable(false)
  ): List[Def.Setting[_]] = {
    val f1 = acyclic match {
      case Enable(x) => forceAcyclicity(x)
      case Disable => Nil
    }

    val f2 = fatalWarnings match {
      case Enable(x) => forceFatalWarnings(x)
      case Disable => Nil
    }

    val f3 = enableWarts match {
      case Enable(true) => List(wartremover.wartremoverWarnings in (Compile, compile) ++= warts)
      case Enable(false) => List(wartremover.wartremoverWarnings ++= warts)
      case Disable => Nil
    }

    f1 ++ f2 ++ f3
  }
}

object Dependencies {
  object Plugin {
    val kindProjector           = "org.typelevel"         %% "kind-projector"     % "0.10.1"
    val betterMonadicFor        = "com.olegpy"            %% "better-monadic-for" % "0.3.0"
    val silencerPlugin          = "com.github.ghik"       %% "silencer-plugin"    % "1.3.3"
    val silencer                = "com.github.ghik"       %% "silencer-lib"       % "1.3.3" % Provided
    val splain                  = "io.tryp"                % "splain"             % "0.4.1" cross CrossVersion.patch
    val nonExhaustiveMatchError = "com.softwaremill.neme" %% "neme-plugin"        % "0.0.2"
  }

  val asm         = "org.ow2.asm" % "asm" % "8.0.1"
  val asmAnalysis = "org.ow2.asm" % "asm-analysis" % "8.0.1"
  val asmTree     = "org.ow2.asm" % "asm-tree" % "8.0.1"
  val asmUtil     = "org.ow2.asm" % "asm-util" % "8.0.1"
  val asmCommons  = "org.ow2.asm" % "asm-commons" % "8.0.1"

  val fastutil         = "it.unimi.dsi" % "fastutil" % "8.2.2"

  val spire       = "org.typelevel"  %% "spire"        % "0.17.0-M1"
  val zio         = "dev.zio"        %% "zio"          % "1.0.0-RC18-2"
  val zioStream   = "dev.zio"        %% "zio-streams"  % "1.0.0-RC18-2"
  val magnolia    = "me.lyh"         %% "magnolia"     % "0.10.1-jto"

  val scalaTest  = "org.scalatest"  %% "scalatest"  % "3.0.7"
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.14.0"
}
