import sbt.Package._
import sbt._
import sbt.Keys._
import scala.language.postfixOps

object BuildSettings {

  import uk.gov.hmrc.gitstamp.GitStampPlugin

  val filter = { (ms: Seq[(File, String)]) =>
    ms filter {
      case (file, path) =>
        path != "logback.xml" && !path.startsWith("toignore") && !path.startsWith("samples")
    }
  }

  val buildSettings = Defaults.coreDefaultSettings ++
    Seq(
      organization := "org.reactivemongo",
      scalaVersion := "2.11.6",
      crossScalaVersions  := Seq("2.11.6", "2.10.5"),
      crossVersion := CrossVersion.binary,
      javaOptions in test ++= Seq("-Xmx512m", "-XX:MaxPermSize=512m"),
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-target:jvm-1.6", "-Xlint", "-Xmax-classfile-name", "100", "-encoding", "UTF-8"),
      scalacOptions in (Compile, doc) ++= Seq("-unchecked", "-deprecation", "-diagrams", "-implicits", "-skip-packages", "samples"),
      scalacOptions in (Compile, doc) ++= Opts.doc.title("ReactiveMongo API"),
      mappings in (Compile, packageBin) ~= filter,
      mappings in (Compile, packageSrc) ~= filter,
      mappings in (Compile, packageDoc) ~= filter,
      isSnapshot := version.value.matches("([\\w]+\\-SNAPSHOT)|([\\.\\w]+)\\-([\\d]+)\\-([\\w]+)")
    ) ++
    Publish.settings ++
    GitStampPlugin.gitStampSettings ++
    gitStampInfo()

  private def gitStampInfo() = {
    import uk.gov.hmrc.gitstamp.GitStamp._

    Seq(packageOptions <+= (packageOptions in Compile, packageOptions in packageBin) map {(a, b) =>
      ManifestAttributes(gitStamp.toSeq: _*)})
  }

}

object Publish {
  def targetRepository: Def.Initialize[Option[Resolver]] = Def.setting {
    val nexus = "https://oss.sonatype.org/"
    val snapshotsR = "snapshots" at nexus + "content/repositories/snapshots"
    val releasesR  = "releases"  at nexus + "service/local/staging/deploy/maven2"
    val resolver = if (isSnapshot.value) snapshotsR else releasesR
    Some(resolver)
  }

  lazy val settings = Seq(
    publishMavenStyle := true,
    publishTo := targetRepository.value,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("http://reactivemongo.org")),
    pomExtra := (
      <scm>
        <url>git://github.com/ReactiveMongo/ReactiveMongo.git</url>
        <connection>scm:git://github.com/ReactiveMongo/ReactiveMongo.git</connection>
      </scm>
      <developers>
        <developer>
          <id>sgodbillon</id>
          <name>Stephane Godbillon</name>
          <url>http://stephane.godbillon.com</url>
        </developer>
      </developers>))
}

object Format {
  import com.typesafe.sbt.SbtScalariform._

  lazy val settings = scalariformSettings ++ Seq(
    ScalariformKeys.preferences := formattingPreferences)

  lazy val formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences().
      setPreference(AlignParameters, true).
      setPreference(AlignSingleLineCaseStatements, true).
      setPreference(CompactControlReadability, false).
      setPreference(CompactStringConcatenation, false).
      setPreference(DoubleIndentClassDeclaration, true).
      setPreference(FormatXml, true).
      setPreference(IndentLocalDefs, false).
      setPreference(IndentPackageBlocks, true).
      setPreference(IndentSpaces, 2).
      setPreference(MultilineScaladocCommentsStartOnFirstLine, false).
      setPreference(PreserveSpaceBeforeArguments, false).
      setPreference(PreserveDanglingCloseParenthesis, false).
      setPreference(RewriteArrowSymbols, false).
      setPreference(SpaceBeforeColon, false).
      setPreference(SpaceInsideBrackets, false).
      setPreference(SpacesWithinPatternBinders, true)
  }
}

object Dependencies {
  val netty = "io.netty" % "netty" % "3.6.5.Final" cross CrossVersion.Disabled

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.3.6"

  val iteratees = "com.typesafe.play" %% "play-iteratees" % "2.3.5"

  val specs = "org.specs2" %% "specs2-core" % "2.3.11" % "test"

  val log4jVersion = "2.0.2"
  val log4j = Seq("org.apache.logging.log4j" % "log4j-api" % log4jVersion, "org.apache.logging.log4j" % "log4j-core" % log4jVersion)
}

object ReactiveMongoBuild extends Build {
  import BuildSettings._
  import Dependencies._
  import sbtunidoc.{ Plugin => UnidocPlugin }
  import uk.gov.hmrc.versioning.SbtGitVersioning

  val projectPrefix = "ReactiveMongo"

  val resolversList = Seq(Resolver.typesafeRepo("snapshots"), Resolver.typesafeRepo("releases"))

  lazy val reactivemongo =
    Project(
      s"$projectPrefix-Root",
      file("."),
      settings = buildSettings ++ (publishArtifact := false) )
    .settings(UnidocPlugin.unidocSettings: _*)
    .aggregate(driver, bson, bsonmacros)

  lazy val driver = Project(
    projectPrefix,
    file("driver"),
    settings = buildSettings ++ Seq(
      resolvers := resolversList,
      libraryDependencies ++= Seq(
        netty,
        akkaActor,
        iteratees,
        specs) ++ log4j))
    .enablePlugins(SbtGitVersioning)
    .dependsOn(bsonmacros)

  lazy val bson = Project(
    s"$projectPrefix-BSON",
    file("bson"),
    settings = buildSettings)
    .enablePlugins(SbtGitVersioning)
    .settings(libraryDependencies += Dependencies.specs)

  lazy val bsonmacros = Project(
    s"$projectPrefix-BSON-Macros",
    file("macros"),
    settings = buildSettings ++ Seq(
      libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
    ))
    .enablePlugins(SbtGitVersioning)
    .settings(libraryDependencies += Dependencies.specs)
    .dependsOn(bson)
}
