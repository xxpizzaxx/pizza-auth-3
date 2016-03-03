package moe.pizza.auth

import java.io.File

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import moe.pizza.auth.ldap.server.EmbeddedLdapServer
import moe.pizza.auth.webapp.Webapp
import scopt.OptionParser

import scala.io.Source
import scala.util.{Failure, Try}

/**
  * Created by andi on 19/02/16.
  */
object Main {
  case class RunOptions(servers: Option[ServerOptions], config: File = new File("./config.yml"))
  case class ServerOptions(ldap: Boolean = false, webinterface: Boolean = false, restapi: Boolean = false)

  val parser = new OptionParser[RunOptions]("pizza-auth") {
    head("pizza-auth-3 command line interface")
    help("help") text ("prints this usage text")
    arg[File]("<config file>") optional() action { (x, c) =>
      c.copy(config = x)
    } text ("configuration file (optional)")
    cmd("server") action { (_, c) =>
      c.copy(servers = Some(ServerOptions()))
    } text ("run pizza-auth server(s)") children(
        opt[Boolean]("ldap") action { (x, c) =>
          c.copy(servers = c.servers.map(_.copy(ldap = x)))
        } text("enable the built in LDAP server"),
        opt[Boolean]("webinterface") action { (x, c) =>
          c.copy(servers = c.servers.map(_.copy(webinterface = x)))
        } text("enable the main web interface"),
        opt[Boolean]("restapi") action { (x, c) =>
          c.copy(servers = c.servers.map(_.copy(restapi = x)))
        } text("enable the rest API")
      )
  }


  def parseConfigFile(f: File): Try[config.ConfigFile.ConfigFile] = {
    val OM = new ObjectMapper(new YAMLFactory())
    OM.registerModule(DefaultScalaModule)
    Try {
      OM.readValue[config.ConfigFile.ConfigFile](Source.fromFile(f).mkString, classOf[config.ConfigFile.ConfigFile])
    }
  }

  def main(args: Array[String]): Unit = {
    val config = parser.parse(args, RunOptions(None))
    val configfile = parseConfigFile(config.get.config)
    configfile match {
      case Failure(f) =>
        System.err.println("Unable to read configuration file: %s".format(f))
      case _ => ()
    }
    config match {
      case Some(c) =>
        c.servers match {
          case Some(s) =>
            if (s.ldap) {
              //new EmbeddedLdapServer()
            }
            if (s.webinterface) {
              import scala.concurrent.ExecutionContext.Implicits.global
              val graders = configfile.get.auth.constructGraders()
              val webapp = new Webapp(configfile.get, graders, 9021, null)
              webapp.start()
            }
        }
    }
  }

}
