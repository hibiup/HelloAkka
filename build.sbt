name := "HelloAkka"

version := "1.0"

scalaVersion := "2.12.4"

//resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

val akkaVersion = "2.5.11"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion