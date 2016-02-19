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
// tests
libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"

enablePlugins(SbtTwirl)