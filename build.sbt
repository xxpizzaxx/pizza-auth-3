name := "pizza-auth-3"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += Resolver.jcenterRepo

libraryDependencies += "com.sparkjava" % "spark-core" % "2.3"
libraryDependencies += "moe.pizza" %% "eveapi" % "0.33"
libraryDependencies += "org.log4s" %% "log4s" % "1.2.1"
libraryDependencies += "org.apache.directory.server" % "apacheds-all" % "2.0.0-M21"

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"

Twirl.settings
