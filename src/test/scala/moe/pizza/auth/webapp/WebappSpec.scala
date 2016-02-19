package moe.pizza.auth.webapp

import java.net.{InetSocketAddress, Socket, ServerSocket}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import dispatch._
import moe.pizza.auth.config.ConfigFile.ConfigFile
import org.scalatest.{FlatSpec, MustMatchers}
import org.scalatest.mock.MockitoSugar
import spark.Spark
import moe.pizza.eveapi.SyncableFuture

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.io.Source
import scala.util.Try

/**
  * Created by Andi on 19/02/2016.
  */
class WebappSpec extends FlatSpec with MustMatchers with MockitoSugar {

  def withPort(b: Int => Unit) {
    val port = claimPort()
    b(port)
    waitForPortToFree(port)
  }

  def claimPort(): Int = {
    val s = new ServerSocket(0)
    val port = s.getLocalPort
    s.close()
    port
  }

  def waitForPortToFree(p: Int) = {
    val f = Future { Try {
      val s = new Socket()
      s.connect(new InetSocketAddress("127.0.0.1", p), 10000)
    }}
    Await.result(f, 10.seconds)
  }

  val OM = new ObjectMapper(new YAMLFactory())
  OM.registerModule(DefaultScalaModule)

  "Webapp" should "start up cleanly" in {
    withPort { port =>
      val config = Source.fromURL(getClass.getResource("/config.yml")).getLines().mkString("\n")
      val conf = OM.readValue[ConfigFile](config, classOf[ConfigFile])
      val w = new Webapp(conf, port)
      w.start()
      Spark.stop()
    }
  }

  "Webapp" should "serve the landing page" in {
    withPort { port =>
      val config = Source.fromURL(getClass.getResource("/config.yml")).getLines().mkString("\n")
      val conf = OM.readValue[ConfigFile](config, classOf[ConfigFile])
      val w = new Webapp(conf, port)
      w.start()
      val r = Http(url(s"http://localhost:$port/").GET).sync()
      r.getStatusCode must equal(200)
      r.getResponseBody.trim must equal(templates.html.base.apply("pizza-auth-3", templates.html.landing.apply(), None).toString().trim)
      Spark.stop()
    }
  }

}
