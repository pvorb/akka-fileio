name := "akka-fileio"

organization := "de.vorb"

version := "0.0.0-SNAPSHOT"

scalaVersion := "2.10.0"


homepage := Some(url("https://github.com/pvorb/akka-fileio"))

licenses := Seq("MIT License" -> url("http://vorb.de/license/mit.html"))

mainClass := None


// Dependencies
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "org.scala-lang" % "scala-actors" % "2.10.0"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.1.0"


// Publishing information
publishMavenStyle := true

publishTo <<= version { (version: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (version.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <scm>
    <url>git@github.com:pvorb/akka-fileio.git</url>
    <connection>scm:git:git@github.com:pvorb/akka-fileio.git</connection>
  </scm>
  <developers>
    <developer>
      <id>pvorb</id>
      <name>Paul Vorbach</name>
      <email>paul@vorb.de</email>
      <url>http://paul.vorba.ch/</url>
      <timezone>+1</timezone>
    </developer>
  </developers>)

