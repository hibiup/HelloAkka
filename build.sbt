name := "HelloAkka"

version := "1.0"

scalaVersion := "2.12.5"
val akkaVersion = "2.5.17"
val scalaTestVersion = "3.0.5"
val typesafeConfigVersion = "1.3.2"
val akkaHttpVersion = "10.1.5"
val akkaStreamVersion = "2.5.12"
val akkaTestkitVersion = "2.5.12"
val scalaMockVersion = "4.1.0"
val scalaMockAkkaSupportVersion = "3.6.0"

//resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % akkaTestkitVersion % Test,
    "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
    "org.scalamock" %% "scalamock-scalatest-support" % scalaMockAkkaSupportVersion % Test,
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "com.typesafe" % "config" % typesafeConfigVersion
)