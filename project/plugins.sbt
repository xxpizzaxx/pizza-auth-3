logLevel := Level.Warn

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.1")

resolvers += "spray repo" at "http://repo.spray.io"
addSbtPlugin("io.spray" % "sbt-twirl" % "0.7.0")
