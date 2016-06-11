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
      expiration = Some(Instant.now.plusSeconds(86400).getEpochSecond), // lasts one day, for now
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
    val processedHeaders = req.headers.get(headers.Cookie).toList.flatMap(_.values.list).map { header =>
      val session = JwtCirce.decodeJson(header.content, secretKey, Seq(JwtAlgorithm.HS256)).toOption.flatMap { jwt =>
        jwt.as[MyJwt].toOption
      }.flatMap{ myjwt =>
        sessions.get(myjwt.id)
      }
      (header, session)
    }
    val (successful, failed) = processedHeaders.partition(_._2.isDefined)
    log.info("Finished processing headers")
    log.info(s"Successful sessions: ${successful}")
    log.info(s"Failed sessions: ${failed}")

    // old logic
    val cookie = req.headers.get(headers.Cookie).flatMap(_.values.stream.find(_.name == COOKIESESSION))
    val (r, id) = cookie match {
      case None =>
        log.info("user has no session, creating it")
        // they have no session
        newSession(s)(req)
      case Some(c) =>
        JwtCirce.decodeJson(c.content, secretKey, Seq(JwtAlgorithm.HS256)) match {
          case Success(jwt) =>
            jwt.as[MyJwt].toOption match {
              case Some(myjwt) =>
                sessions.get(myjwt.id) match {
                  case Some(rs) =>
                    log.info("cookie matched up, passing session through")
                    (s(req.copy(attributes = req.attributes.put(SESSION, rs))), myjwt.id)
                  case None =>
                    // session has expired or been deleted
                    log.info("session has expired, making a new one")
                    newSession(s)(req)
                }
              case None =>
                // can't parse the JWT
                log.info("JWT was invalid or malformed, making a new session")
                newSession(s)(req)
            }
          case Failure(t) =>
            // make a new session and remove the old session
            log.info("generally failed to check if they had a session, making a new session")
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
      resp.attributes.get(LOGOUT) match {
        case Some(_) =>
          log.info("logout flag set, logging out")
          resp.removeCookie(COOKIESESSION)
        case None => resp
      }
    }
  }
}
