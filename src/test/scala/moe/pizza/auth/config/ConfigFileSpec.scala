package moe.pizza.auth.config

import java.io.File

import moe.pizza.auth.Main
import org.scalatest.{FlatSpec, MustMatchers}

class ConfigFileSpec extends FlatSpec with MustMatchers {

  "ConfigFile" should "represent a .yml file" in {
    val c = Main.parseConfigFile(new File("config.yml"))
    c.isSuccess must equal(true)
    c.get.auth.alliance must equal("Confederation of xXPIZZAXx")
  }

}
