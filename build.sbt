val akkaVersion = "2.5.22"
val scalaTestVersion = "3.0.5"
val typesafeConfigVersion = "1.3.2"
val akkaHttpVersion = "10.1.5"
val akkaStreamVersion = "2.5.22"
val akkaTestkitVersion = "2.5.22"
val scalaMockVersion = "4.1.0"
val scalaMockAkkaSupportVersion = "3.6.0"
val catsVersion = "1.6.0"
val slickVersion = "3.2.3"
val H2Version = "1.4.197"
val logbackVersion = "1.2.3"

lazy val root = (project in file("."))
        .settings(
            name := "HelloAkka",
            version := "1.0",
            scalaVersion := "2.12.8",
            resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
            libraryDependencies ++= Seq(
                "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
                "com.typesafe.akka" %% "akka-testkit" % akkaTestkitVersion % Test,
                "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
                "org.scalamock" %% "scalamock-scalatest-support" % scalaMockAkkaSupportVersion % Test,
                // logging
                "ch.qos.logback" % "logback-classic" % logbackVersion,
                "com.typesafe.akka" %% "akka-slf4j" % "2.5.22",
                // Akka Actor, Stream and Http
                "com.typesafe.akka" %% "akka-actor" % akkaVersion,
                "com.typesafe.akka" %% "akka-stream" % akkaVersion,
                "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
                "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
                // Akka persistant
                "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
                "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.5.0",
                "org.iq80.leveldb" % "leveldb" % "0.7",
                "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
                // JDBC
                "com.typesafe.slick" %% "slick" % slickVersion,
                "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
                "com.h2database" % "h2" % H2Version,
                // Others
                "com.typesafe" % "config" % typesafeConfigVersion,
                "org.typelevel" %% "cats-core" % catsVersion,
            ),
            //addSbtPlugin("org.lyranthe.sbt" % "partial-unification" % "1.1.2")
            scalacOptions ++= Seq(
                "-language:higherKinds",
                "-deprecation",
                "-encoding", "UTF-8",
                "-Ypartial-unification",
                "-feature",
                "-language:_"
            )
        )