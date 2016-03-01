name := "pizza-auth-3"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += Resolver.jcenterRepo
resolvers += Resolver.bintrayRepo("andimiller", "maven") // while things sync to jcenter

// main dependencies
libraryDependencies ++= Seq(
  // frameworks
  "com.sparkjava"                    % "spark-core"                 % "2.3",
  // supporting libraries
  "moe.pizza"                        %% "eveapi"                    % "0.36",
  "org.log4s"                        %% "log4s"                     % "1.2.1",
  "com.github.scopt"                 %% "scopt"                     % "3.3.0",
  "com.googlecode.gettext-commons"   % "gettext-maven-plugin"       % "1.2.4",
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml"    % "2.6.1",
  "net.andimiller"                   %% "integrated-query-language" % "1.1",
  // embedded services
  "org.apache.directory.server"      % "apacheds-all"               % "2.0.0-M21",
  "org.apache.kafka"                 %% "kafka"                     % "0.8.2.2" exclude("org.slf4j", "slf4j-log4j12")
)

// test frameworks and tools
libraryDependencies ++= Seq(
  "org.scalatest"  %% "scalatest"  % "2.2.4"   % "test",
  "org.mockito"    % "mockito-all" % "1.10.19" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.0"  % "test"
)

enablePlugins(SbtTwirl)

coverageExcludedPackages := "templates\\.html\\.*;moe\\.pizza\\.auth\\.Main;moe\\.pizza\\.auth\\.queue.*"

testOptions in Test += Tests.Argument(TestFrameworks.ScalaCheck, "-maxSize", "5", "-minSuccessfulTests", "33", "-workers", "1", "-verbosity", "1")