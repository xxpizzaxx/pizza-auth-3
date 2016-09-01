package moe.pizza.auth.webapp

import java.net.{Socket, InetSocketAddress, ServerSocket}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import moe.pizza.auth.config.ConfigFile.ConfigFile

import scala.concurrent.{Future, Await}
import scala.io.Source
import scala.util.Try
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by Andi on 20/02/2016.
  */
object WebappTestSupports {
  val OM = new ObjectMapper(new YAMLFactory())
  OM.registerModule(DefaultScalaModule)

  def readTestConfig(): ConfigFile = {
    val config = Source
      .fromURL(getClass.getResource("/config.yml"))
      .getLines()
      .mkString("\n")
    val conf = OM.readValue[ConfigFile](config, classOf[ConfigFile])
    conf
  }

}
