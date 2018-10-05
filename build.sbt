name := "HelloAkka"

version := "1.0"

scalaVersion := "2.12.4"
val akkaVersion = "2.5.11"
val scalaTestVersion = "3.0.5"

//resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
libraryDependencies += "org.scalatest" %% "scalatest" % scalaTestVersion
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test