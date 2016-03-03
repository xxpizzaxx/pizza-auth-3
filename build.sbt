name := "pizza-auth-3"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += Resolver.jcenterRepo

fork := true

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
  "com.github.tototoshi"             %% "scala-csv"                 % "1.3.0",
  "javax.persistence"                % "persistence-api"            % "1.0.2",
  "com.orientechnologies"            % "orientdb-client"            % "2.1.12",
  "com.orientechnologies"            % "orientdb-graphdb"           % "2.1.12",
  "com.tinkerpop.blueprints"         % "blueprints"                 % "2.6.0",

  // embedded services
  "org.apache.directory.server"      % "apacheds-all"               % "2.0.0-M21",
  "org.apache.kafka"                 %% "kafka"                     % "0.8.2.2" exclude("org.slf4j", "slf4j-log4j12"),
  "com.orientechnologies"            % "orientdb-server"            % "2.1.12"

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