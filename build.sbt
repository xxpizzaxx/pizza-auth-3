name := "pizza-auth-3"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += Resolver.jcenterRepo

// web framework
libraryDependencies += "com.sparkjava" % "spark-core" % "2.3"
// eve online APIs
libraryDependencies += "moe.pizza" %% "eveapi" % "0.34"
// logging
libraryDependencies += "org.log4s" %% "log4s" % "1.2.1"
// LDAP
libraryDependencies += "org.apache.directory.server" % "apacheds-all" % "2.0.0-M21"
// translations
libraryDependencies += "com.googlecode.gettext-commons" % "gettext-maven-plugin" % "1.2.4"
// YAML
libraryDependencies += "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.6.1"
// command line interface
libraryDependencies += "com.github.scopt" %% "scopt" % "3.3.0"
// queues
libraryDependencies += "org.apache.kafka" %% "kafka" % "0.8.2.2" exclude("org.slf4j", "slf4j-log4j12")
// tests
libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
libraryDependencies += "org.mockito" % "mockito-all" % "1.10.19" % "test"
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.13.0" % "test"

enablePlugins(SbtTwirl)

coverageExcludedPackages := "templates\\.html\\.*;moe\\.pizza\\.auth\\.Main;moe\\.pizza\\.auth\\.queue.*"

testOptions in Test += Tests.Argument(TestFrameworks.ScalaCheck, "-maxSize", "5", "-minSuccessfulTests", "33", "-workers", "1", "-verbosity", "1")