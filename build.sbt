val Http4sVersion = "0.19.0-M4"
val CirceVersion = "0.10.0"
val Specs2Version = "4.2.0"
val LogbackVersion = "1.2.3"

lazy val root = (project in file("."))
  .settings(
    organization := "com.example",
    name := "http4s-sandbox",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.11.12",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s" %% "http4s-circe" % Http4sVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,
      "org.specs2" %% "specs2-core" % Specs2Version % "test",
      "ch.qos.logback" % "logback-classic" % LogbackVersion
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4")
  )



libraryDependencies += "co.fs2" %% "fs2-core" % "1.0.0"
libraryDependencies += "co.fs2" %% "fs2-io" % "1.0.0"
libraryDependencies += "co.fs2" %% "fs2-reactive-streams" % "1.0.0"
libraryDependencies += "co.fs2" %% "fs2-experimental" % "1.0.0"
libraryDependencies += "org.apache.spark" %% "spark-core" % "2.3.2"
libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.3.2"


scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Ypartial-unification",
)
