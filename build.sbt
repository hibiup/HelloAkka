
val ver = new {
  val akka = "2.6.1"
  val akkaHttp = "10.1.11"
  val config = "1.4.1"
  val logback = "1.2.6"
  val monix = "3.4.0"
  val scalaLog = "3.9.4"
  val slick = "3.3.3"
  val h2 = "1.4.200"
}

// DEPENDENCIES
lazy val testing = Seq(
  "org.scalatest" %% "scalatest" % "3.0.8"
  ,"com.typesafe.akka" %% "akka-multi-node-testkit" % ver.akka
  ,"com.typesafe.akka" %% "akka-testkit" % ver.akka
)

lazy val logging = Seq(
  "ch.qos.logback" % "logback-classic" % ver.logback
  ,"com.typesafe.scala-logging" %% "scala-logging" % ver.scalaLog
  ,"com.typesafe.akka" %% "akka-slf4j" % ver.akka
)

lazy val config = {
  Seq(
    "com.typesafe" % "config" % ver.config
  )
}

lazy val akka = {
  Seq(
    "com.typesafe.akka" %% "akka-actor" % ver.akka,
    "com.typesafe.akka" %% "akka-stream" % ver.akka,
    "com.typesafe.akka" %% "akka-cluster" % ver.akka,
    "com.typesafe.akka" %% "akka-cluster-metrics" % ver.akka,
    "com.typesafe.akka" %% "akka-serialization-jackson" % ver.akka,
    "com.typesafe.akka" %% "akka-http" % ver.akkaHttp,
    "com.typesafe.akka" %% "akka-http-spray-json" % ver.akkaHttp,
    "com.typesafe.akka" %% "akka-cluster-tools" % ver.akka,
    "com.typesafe.akka" %% "akka-persistence" % ver.akka
  )
}

lazy val database = {
  Seq(
    "com.typesafe.slick" %% "slick" % ver.slick
    ,"com.typesafe.slick" %% "slick-hikaricp" % ver.slick
    ,"com.h2database" % "h2" % ver.h2
  )
}

lazy val twirl = {
  Seq(
    "com.typesafe.play" %% "twirl-api"  % "1.5.0"
  )
}

lazy val monix = {
  Seq(
    "io.monix" %% "monix" % ver.monix
  )
}

lazy val compilerOptions = Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  //"-Ypartial-unification",
  "-Xfatal-warnings"
)

lazy val root = (project in file("."))
  .settings(
    name := "HelloAkka"
    ,version := "1.0"
    //scalaVersion := "2.12.8",
    ,scalaVersion := "2.13.6"
    //resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    ,libraryDependencies ++= testing.map(_ % Test) ++ logging++ config ++ akka ++ twirl ++ monix ++ database ++ Seq(
      // Akka persistant
      //"com.github.dnvriend" %% "akka-persistence-jdbc" % "3.5.0",
      //"org.iq80.leveldb" % "leveldb" % "0.7",s
      //"org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
    )
    //addSbtPlugin("org.lyranthe.sbt" % "partial-unification" % "1.1.2")
    ,scalacOptions ++= compilerOptions
  ).enablePlugins(SbtTwirl)