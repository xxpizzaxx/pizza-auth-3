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
  val COOKIESESSION = "authsessionid"
}

class SessionManager(secretKey: String) extends HttpMiddleware {
  val log = LoggerFactory.getLogger(getClass)
  val sessions = TrieMap[String, Session2]()

  implicit def jodainstant2javainstant(jodai: org.joda.time.Instant): Instant = Instant.parse(jodai.toString)

  def newSession(): (String, Session2) = {
    val sessionid = UUID.randomUUID().toString
    val session = new Session2(List.empty, None)
    val claim = JwtClaim(
      expiration = Some(Instant.now.plusSeconds(86400*30).getEpochSecond), // lasts 30 days
      issuedAt = Some(Instant.now.getEpochSecond)
    ) +("id", sessionid)
    val token = JwtCirce.encode(claim, secretKey, JwtAlgorithm.HS256)
    (sessionid, session)
  }

  case class MyJwt(exp: Long, iat: Long, id: String)

  override def apply(s: HttpService): HttpService = Service.lift { req =>
    log.info(s"Intercepting request ${req}")
    val processedHeaders = req.headers.get(headers.Cookie).toList.flatMap(_.values.list).map { header =>
      val session = JwtCirce.decodeJson(header.content, secretKey, Seq(JwtAlgorithm.HS256)).toOption.flatMap { jwt =>
        jwt.as[MyJwt].toOption
      }.flatMap{ myjwt =>
        sessions.get(myjwt.id).map(x => (myjwt.id, x))
      }
      (header, session)
    }
    val (successful, failed) = processedHeaders.partition(_._2.isDefined)
    log.info("Finished processing headers")
    log.info(s"Successful sessions: ${successful}")
    log.info(s"Failed sessions: ${failed}")

    // if we didn't find a valid session, make them one
    val (sessionid, session) = successful.headOption.flatMap(_._2).getOrElse(newSession())
    val removeme = successful.tail ++ failed

    // do the inner request
    val response = s(req.copy(attributes = req.attributes.put(SESSION, session)))

    response.map { resp =>
      // do all of this once the request has been created
      resp.attributes.get(SESSION).getOrElse(session)
    }

    response.map { resp =>
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
