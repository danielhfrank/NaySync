name := "NaySync"

version := "1.0"

scalaVersion := "2.12.1"

libraryDependencies += "com.twitter" % "finagle-http_2.12" % "17.11.0"
libraryDependencies += "com.twitter" % "bijection-core_2.12" % "0.9.6"

val circeVersion = "0.8.0"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)