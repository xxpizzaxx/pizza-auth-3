package moe.pizza.auth.webapp

import java.time.Instant

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import moe.pizza.auth.interfaces.UserDatabase
import moe.pizza.auth.webapp.SessionManager._
import moe.pizza.auth.webapp.Types.{HydratedSession, Session, Session2}
import org.http4s.{HttpService, _}
import org.http4s.server._
import org.slf4j.LoggerFactory
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import io.circe.generic.auto._
import Utils._
import io.circe.Decoder.Result

import scala.util.Try

object SessionManager {
  val HYDRATEDSESSION = AttributeKey[HydratedSession]("HYDRATEDSESSION")
  val LOGOUT = AttributeKey[String]("LOGOUT")
  val COOKIESESSION = "authsession"
}

class SessionManager(secretKey: String, ud: UserDatabase)
    extends HttpMiddleware {
  val log = LoggerFactory.getLogger(getClass)
  val OM = new ObjectMapper()
  OM.registerModule(DefaultScalaModule)

  case class MyJwt(exp: Long, iat: Long, session: String)

  implicit def toOption[A](e: Result[A]): Option[A] = {
    e match {
      case Left(_) => None
      case Right(a) => Some(a)
    }
  }

  override def apply(s: HttpService): HttpService = Service.lift { req =>
    log.info(s"Intercepting request ${req}")
    // TODO: this used to be nice with toOption, what happened
    val sessions =
      req.headers.get(headers.Cookie).toList.flatMap(_.values.list).flatMap {
        header =>
          JwtCirce.decodeJson(header.content, secretKey, Seq(JwtAlgorithm.HS256))
            .toOption
            .flatMap { jwt =>
              jwt.as[MyJwt] match {
                case Right(x) => Some(x)
                case Left(_) => None
              }
            }
            .flatMap { myjwt =>
              Try { OM.readValue(myjwt.session, classOf[Session2]) }.toOption
            }
      }
    log.info(s"found sessions: ${sessions}")

    // if we didn't find a valid session, make them one
    val session =
      sessions.headOption.getOrElse(Session2(List.empty, None, None, None))

    // do the inner request
    val hydrated = session.hydrate(ud)
    log.info(s"running inner router with hydrated session ${hydrated}")
    val response =
      s(req.copy(attributes = req.attributes.put(HYDRATEDSESSION, hydrated)))

    response.map { resp =>
      // do all of this once the request has been created
      val sessionToSave = resp.attributes
        .get(HYDRATEDSESSION)
        .map(_.dehydrate())
        .getOrElse(session)
      val oldsessions = resp.headers
        .get(headers.Cookie)
        .toList
        .flatMap(_.values.list)
        .filter(_.name == COOKIESESSION)
      if (resp.attributes.get(LOGOUT).isEmpty) {
        log.info(s"saving the session as a cookie")
        val claim = JwtClaim(
            expiration = Some(
              Instant.now
                .plusSeconds(86400 * 30)
                .getEpochSecond), // lasts 30 days
            issuedAt = Some(Instant.now.getEpochSecond)
          ) + ("session", OM.writeValueAsString(sessionToSave))
        val token = JwtCirce.encode(claim, secretKey, JwtAlgorithm.HS256)
        resp.addCookie(
          new Cookie(COOKIESESSION, token, None, None, None, path = Some("/"))
        )
      } else {
        log.info(s"log out flag was set, not saving any cookies")
        resp.removeCookie(COOKIESESSION)
      }
    }
  }
}
