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
  val COOKIESESSION = "jwetsession"
}

class SessionManager(secretKey: String) extends HttpMiddleware {
  val log = LoggerFactory.getLogger(getClass)
  val sessions = TrieMap[String, Session2]()

  implicit def jodainstant2javainstant(jodai: org.joda.time.Instant): Instant = Instant.parse(jodai.toString)

  def newSession(s: HttpService)(req: Request): (Task[Response], String) = {
    val sessionid = UUID.randomUUID().toString
    val session = new Session2(List.empty, None)
    val claim = JwtClaim(
      expiration = Some(Instant.now.plusSeconds(86400 * 30).getEpochSecond), // lasts thirty days
      issuedAt = Some(Instant.now.getEpochSecond)
    ) +("id", sessionid)
    val token = JwtCirce.encode(claim, secretKey, JwtAlgorithm.HS256)
    sessions.put(sessionid, session)
    val r = s(req.copy(attributes = req.attributes.put(SESSION, session))).map {
      _.addCookie(COOKIESESSION, token, Some(DateTime.now().plusHours(24 * 30).toInstant))
    }
    (r, sessionid)
  }

  case class MyJwt(exp: Long, iat: Long, id: String)


  override def apply(s: HttpService): HttpService = Service.lift { req =>
    log.info(s"Intercepting request ${req}")
    val cookie = req.headers.get(headers.Cookie).flatMap(_.values.stream.find(_.name == COOKIESESSION))
    val (r, id) = cookie match {
      case None =>
        // they have no session
        newSession(s)(req)
      case Some(c) =>
        JwtCirce.decodeJson(c.content, secretKey, Seq(JwtAlgorithm.HS256)) match {
          case Success(jwt) =>
            jwt.as[MyJwt].toOption match {
              case Some(myjwt) =>
                sessions.get(myjwt.id) match {
                  case Some(rs) =>
                    (s(req.copy(attributes = req.attributes.put(SESSION, rs))), myjwt.id)
                  case None =>
                    // session has expired or been deleted
                    newSession(s)(req)
                }
              case None =>
                // can't parse the JWT
                newSession(s)(req)
            }
          case Failure(t) =>
            // make a new session and remove the old session
            newSession(s)(req)
        }
    }
    r.map { resp =>
      log.info("checking inner Response for sessions")
      resp.attributes.get(SESSION) match {
        case Some(newsession) =>
          log.info(s"Session found, storing it in the map: $id => $newsession")
          sessions.put(id, newsession)
        case None =>
          log.info("No session found")
          //sessions.remove(id)
      }
      resp
    }
  }
}
