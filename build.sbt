organization := "com.metamx"

name := "rainer"

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.10.5", "2.11.7")

lazy val root = project.in(file("."))

net.virtualvoid.sbt.graph.Plugin.graphSettings

licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

homepage := Some(url("https://github.com/metamx/rainer"))

publishMavenStyle := true

publishTo := Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2/")

pomIncludeRepository := { _ => false }

pomExtra := (
  <scm>
    <url>https://github.com/metamx/rainer.git</url>
    <connection>scm:git:git@github.com:metamx/rainer.git</connection>
  </scm>
  <developers>
    <developer>
      <name>Gian Merlino</name>
      <organization>Metamarkets Group Inc.</organization>
      <organizationUrl>https://www.metamarkets.com</organizationUrl>
    </developer>
  </developers>)

parallelExecution in Test := false

testOptions += Tests.Argument(TestFrameworks.JUnit, "-Duser.timezone=UTC")

releaseSettings

ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value

val curatorVersion = "2.6.0"

libraryDependencies ++= Seq(
  "com.metamx" %% "scala-util" % "1.11.0",
  "javax.servlet" % "javax.servlet-api" % "3.0.1",
  "org.eclipse.jetty" % "jetty-servlet" % "8.1.10.v20130312",
  "com.google.guava" % "guava" % "15.0"
)

libraryDependencies ++= Seq(
  "org.apache.curator" % "curator-framework" % curatorVersion exclude("org.jboss.netty", "netty"),
  "org.apache.curator" % "curator-recipes" % curatorVersion exclude("org.jboss.netty", "netty"),
  "org.apache.curator" % "curator-x-discovery" % curatorVersion exclude("org.jboss.netty", "netty")
)

libraryDependencies <++= scalaVersion {
  case x if x.startsWith("2.10.") => Seq(
    "org.scalatra" %% "scalatra" % "2.2.1" exclude("com.typesafe.akka", "akka-actor"),
    "org.scalatra" %% "scalatra-test" % "2.2.1" % "test" exclude("com.typesafe.akka", "akka-actor")
  )
  case x if x.startsWith("2.11.") => Seq(
    "org.scalatra" %% "scalatra" % "2.3.1" exclude("com.typesafe.akka", "akka-actor"),
    "org.scalatra" %% "scalatra-test" % "2.3.1" % "test" exclude("com.typesafe.akka", "akka-actor")
  )
}

libraryDependencies <+= scalaVersion {
  case x if x.startsWith("2.10.") => "com.simple" % "simplespec_2.10.2" % "0.8.4" % "test"
  case x if x.startsWith("2.11.") => "com.simple" % "simplespec_2.11" % "0.8.4" % "test"
}

// Test stuff
libraryDependencies ++= Seq(
  "junit" % "junit" % "4.11" % "test",
  "com.novocode" % "junit-interface" % "0.10" % "test",
  "org.apache.derby" % "derby" % "10.10.1.1" % "test",
  "org.apache.curator" % "curator-test" % curatorVersion % "test",
  "ch.qos.logback" % "logback-core" % "1.1.2" % "test",
  "ch.qos.logback" % "logback-classic" % "1.1.2" % "test",
  "org.apache.logging.log4j" % "log4j-to-slf4j" % "2.1" % "test",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.6" % "test",
  "org.slf4j" % "jul-to-slf4j" % "1.7.6" % "test"
)
