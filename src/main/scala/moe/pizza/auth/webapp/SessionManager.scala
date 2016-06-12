package moe.pizza.auth.webapp

import java.time.Instant

import moe.pizza.auth.webapp.SessionManager._
import moe.pizza.auth.webapp.Types.{Session2, Session}
import org.http4s.{HttpService, _}
import org.http4s.server._
import org.slf4j.LoggerFactory
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import io.circe._
import io.circe.generic.semiauto._

object SessionManager {
  val SESSION = AttributeKey[Session2]("SESSION")
  val SESSIONID = AttributeKey[Session2]("SESSIONID")
  val LOGOUT = AttributeKey[String]("LOGOUT")
  val COOKIESESSION = "authsession"
}

class SessionManager(secretKey: String) extends HttpMiddleware {
  val log = LoggerFactory.getLogger(getClass)

  case class MyJwt(exp: Long, iat: Long, session: Session2)
  implicit val myjwtencoder: Encoder[MyJwt] = Encoder[MyJwt]
  implicit val myjwtdecoder: Decoder[MyJwt] = Decoder[MyJwt]

  override def apply(s: HttpService): HttpService = Service.lift { req =>
    log.info(s"Intercepting request ${req}")
    val sessions = req.headers.get(headers.Cookie).toList.flatMap(_.values.list).flatMap { header =>
      JwtCirce.decodeJson(header.content, secretKey, Seq(JwtAlgorithm.HS256)).toOption.flatMap { jwt =>
        jwt.as[MyJwt].toOption
      }.map{ myjwt =>
        myjwt.session
      }
    }
    log.info(s"found sessions: ${sessions}")
    if (sessions.size>1) {
      log.warn(s"found ${sessions.size} sessions, there should be at most 1")
    }

    // if we didn't find a valid session, make them one
    val session = sessions.headOption.getOrElse(Session2(List.empty, None))

    // do the inner request
    val response = s(req.copy(attributes = req.attributes.put(SESSION, session)))

    response.map { resp =>
      // do all of this once the request has been created
      val sessionToSave = resp.attributes.get(SESSION).getOrElse(session)
      val oldsessions = resp.headers.get(headers.Cookie).toList.flatMap(_.values.list).filter(_.name == COOKIESESSION)
      val respWithCookieRemovals = oldsessions.foldLeft(resp){ (resp, cookie) => resp.removeCookie(cookie)}
      if (resp.attributes.get(LOGOUT).isEmpty) {
        log.info(s"saving the session as a cookie")
        val claim = JwtClaim(
          expiration = Some(Instant.now.plusSeconds(86400*30).getEpochSecond), // lasts 30 days
          issuedAt = Some(Instant.now.getEpochSecond)
        ) +("session", sessionToSave)
        val token = JwtCirce.encode(claim, secretKey, JwtAlgorithm.HS256)
        respWithCookieRemovals.addCookie(
          COOKIESESSION,
          token,
          None
        )
      } else {
        log.info(s"log out flag was set, not saving any cookies")
        respWithCookieRemovals
      }
    }
  }
}
