package moe.pizza.auth.webapp

import java.net.{Socket, InetSocketAddress, ServerSocket}
import javax.servlet.http.HttpSession

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import moe.pizza.auth.config.ConfigFile.ConfigFile
import spark._
import spark.route.SimpleRouteMatcher
import spark.routematch.RouteMatch

import scala.concurrent.{Future, Await}
import scala.io.Source
import scala.util.Try
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by Andi on 20/02/2016.
  */
object WebappTestSupports {
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
    Await.result(f, 11.seconds)
  }

  def reflectRoutingTable() = {
    val instance = Spark.getInstance()
    val field = instance.getClass.getDeclaredField("routeMatcher")
    field.setAccessible(true)
    val r = field.get(instance).asInstanceOf[SimpleRouteMatcher]
    r
  }

  def reflectSession(httpSession: HttpSession): Session = {
    val clazz = classOf[Session]
    val constructor = clazz.getDeclaredConstructors.head
    constructor.setAccessible(true)
    val obj = constructor.newInstance(httpSession).asInstanceOf[Session]
    obj
  }

  implicit class PimpedRouteMatch(r: RouteMatch) {
    def handle[T](req: Request, resp: Response): T = {
      r.getTarget.asInstanceOf[RouteImpl].handle(req, resp).asInstanceOf[T]
    }
    def filter[T](req: Request, resp: Response): T = {
      r.getTarget.asInstanceOf[FilterImpl].handle(req, resp).asInstanceOf[T]
    }
  }

  val OM = new ObjectMapper(new YAMLFactory())
  OM.registerModule(DefaultScalaModule)

  def readTestConfig(): ConfigFile = {
    val config = Source.fromURL(getClass.getResource("/config.yml")).getLines().mkString("\n")
    val conf = OM.readValue[ConfigFile](config, classOf[ConfigFile])
    conf
  }

  def resolve(method: spark.route.HttpMethod, path: String, accept: String): RouteMatch = {
    reflectRoutingTable().findTargetForRequestedRoute(method, path, accept)
  }

  import scala.collection.JavaConverters._
  def resolvemulti(method: spark.route.HttpMethod, path: String, accept: String): Seq[RouteMatch] = {
    reflectRoutingTable().findTargetsForRequestedRoute(method, path, accept).asScala
  }


}
