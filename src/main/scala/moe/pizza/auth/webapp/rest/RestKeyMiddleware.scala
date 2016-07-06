package moe.pizza.auth.webapp.rest

import org.http4s.server.HttpMiddleware
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import moe.pizza.auth.interfaces.UserDatabase
import moe.pizza.auth.webapp.SessionManager._
import moe.pizza.auth.webapp.Types.{HydratedSession, Session2, Session}
import org.http4s.{HttpService, _}
import org.http4s.server._
import org.slf4j.LoggerFactory
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import io.circe.generic.auto._
import scala.util.Try
import org.http4s.dsl.{Root, _}

import scalaz.concurrent.Task

class RestKeyMiddleware(apikeys: List[String]) extends HttpMiddleware {
  override def apply(s: HttpService): HttpService = Service.lift { req =>
    req.headers.get(headers.Authorization).map(_.credentials.value.stripPrefix("Bearer ")).filter(apikeys.contains) match {
      case Some(k) =>
        s(req)
      case None =>
        Unauthorized(Challenge(scheme = "Bearer", realm="Please enter a valid API key"))
    }
  }
}
