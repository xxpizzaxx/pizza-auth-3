package moe.pizza.auth.webapp

import java.time.Instant
import java.util.UUID

import moe.pizza.auth.webapp.SessionManager._
import moe.pizza.auth.webapp.Types.{Session2, Session}
import org.http4s.{HttpService, _}
import org.http4s.server._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import io.circe.generic.auto._

import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success}
import scalaz.concurrent.Task


object SessionManager {
  val SESSION = AttributeKey[Session2]("SESSION")
  val SESSIONID = AttributeKey[Session2]("SESSIONID")
  val LOGOUT = AttributeKey[String]("LOGOUT")
  val COOKIESESSION = "authsession"
}

class SessionManager(secretKey: String) extends HttpMiddleware {
  val log = LoggerFactory.getLogger(getClass)

  case class MyJwt(exp: Long, iat: Long, session: Session2)

  override def apply(s: HttpService): HttpService = Service.lift { req =>
    log.info(s"Intercepting request ${req}")
    val processedHeaders = req.headers.get(headers.Cookie).toList.flatMap(_.values.list).map { header =>
      val session = JwtCirce.decodeJson(header.content, secretKey, Seq(JwtAlgorithm.HS256)).toOption.flatMap { jwt =>
        jwt.as[MyJwt].toOption
      }.map{ myjwt =>
        myjwt.session
      }
      session
    }
    val (successful, failed) = processedHeaders.partition(_.isDefined)
    log.info("Finished processing headers")
    log.info(s"Successful sessions: ${successful}")
    log.info(s"Failed sessions: ${failed}")

    // if we didn't find a valid session, make them one
    val session = successful.headOption.getOrElse(Session2(List.empty, None))
    val removeme = successful.tail ++ failed

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
        log.info(s"log out flag was seto, not saving any cookies")
        respWithCookieRemovals
      }
    }
  }
}
