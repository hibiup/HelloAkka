name := "HelloAkka"

version := "1.0"

scalaVersion := "2.12.5"
val akkaVersion = "2.5.17"
val scalaTestVersion = "3.0.5"

//resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion
libraryDependencies += "org.scalatest" %% "scalatest" % scalaTestVersion
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test