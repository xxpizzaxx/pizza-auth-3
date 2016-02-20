package moe.pizza.auth.webapp

import java.net.{Socket, InetSocketAddress, ServerSocket}

import spark.{RouteImpl, Response, Request, Spark}
import spark.route.SimpleRouteMatcher
import spark.routematch.RouteMatch

import scala.concurrent.{Future, Await}
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

  implicit class PimpedRouteMatch(r: RouteMatch) {
    def handle[T](req: Request, resp: Response): T = {
      r.getTarget.asInstanceOf[RouteImpl].handle(req, resp).asInstanceOf[T]
    }
  }
}
