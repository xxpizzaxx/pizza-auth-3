package moe.pizza.auth.webapp

import java.time.Instant
import java.util.UUID

import moe.pizza.auth.config.ConfigFile.ConfigFile
import moe.pizza.auth.graphdb.EveMapDb
import moe.pizza.auth.interfaces.{UserDatabase, PilotGrader}
import moe.pizza.auth.webapp.Types.Session
import moe.pizza.auth.webapp.Utils.Alerts
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi.EVEAPI
import org.http4s
import org.http4s.HttpService
import org.http4s.dsl.Root
import org.http4s.server.middleware.URITranslation
import org.http4s.server.staticcontent.ResourceService
import org.jboss.netty.handler.codec.http.QueryStringDecoder
import org.joda.time.DateTime
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import org.http4s.Http4s._
import org.http4s.headers.{`Content-Type`, `Content-Length`}
import org.http4s._
import org.http4s.MediaType._
import org.http4s.dsl._
import org.http4s.circe._
import org.http4s.server._
import org.http4s.server.middleware.PushSupport._
import org.http4s.server.middleware.authentication._
import org.http4s.twirl._
import scala.collection.concurrent.TrieMap
import scala.util.{Success, Failure}
import scalaz.concurrent.Task
import org.http4s.server.syntax.ServiceOps
import scala.concurrent.ExecutionContext.Implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe._

object NewWebapp {
  val SESSION = AttributeKey[Session]("SESSION")
  val SESSIONID = AttributeKey[Session]("SESSIONID")
  val COOKIESESSION = "jwetsession"
  val PILOT = "pilot"
  val defaultCrestScopes = List("characterLocationRead")
}

class NewWebapp(fullconfig: ConfigFile, graders: PilotGrader, portnumber: Int = 9021, ud: UserDatabase, crestapi: Option[CrestApi] = None, eve: Option[EVEAPI] = None, mapper: Option[EveMapDb] = None) {

  import NewWebapp._

  val log = org.log4s.getLogger
  val config = fullconfig.crest

  val crest = crestapi.getOrElse(new CrestApi(baseurl = config.loginUrl, cresturl = config.crestUrl, config.clientID, config.secretKey, config.redirectUrl))
  val eveapi = eve.getOrElse(new EVEAPI())
  val map = mapper.getOrElse {
    // make the graph database in the webapp for the MVP version
    val map = new EveMapDb("internal-map")
    // initialise the database
    map.provisionIfRequired()
    map
  }

  class SessionManager(secretKey: String) extends HttpMiddleware {
    val sessions = TrieMap[String, Session]()

    implicit def jodainstant2javainstant(jodai: org.joda.time.Instant): Instant = Instant.parse(jodai.toString)

    def newSession(s: HttpService)(req: Request): (Task[Response], String) = {
      val sessionid = UUID.randomUUID().toString
      val session = new Session(List.empty)
      val claim = JwtClaim(
        expiration = Some(Instant.now.plusSeconds(86400).getEpochSecond), // lasts one day
        issuedAt = Some(Instant.now.getEpochSecond)
      ) +("id", sessionid)
      val token = JwtCirce.encode(claim, secretKey, JwtAlgorithm.HS256)
      println(token)
      sessions.put(sessionid, session)
      val r = s(req.copy(attributes = req.attributes.put(SESSION, session))).map {
        _.addCookie(COOKIESESSION, token, Some(DateTime.now().plusHours(24).toInstant))
      }
      (r, sessionid)
    }

    case class MyJwt(exp: Long, iat: Long, id: String)


    override def apply(s: HttpService): HttpService = Service.lift { req =>
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
      r.map{ resp =>
        resp.attributes.get(SESSION).foreach { newsession =>
          sessions.put(id, newsession)
        }
        resp
      }
    }
  }

  def staticrouter = staticcontent.resourceService(ResourceService.Config("/static/static/", "/static/"))

  import Utils._

  def dynamicrouter = HttpService {
    case req@GET -> Root => {
      val newsession = req.flash(Alerts.info, "hi, you have a session")
      println(req.headers.get(headers.Cookie))
      println(req.getSession)
      Ok(templates.html.base("test-page", templates.html.landing(), req.getSession, None))
          .map(_.withAttribute(SESSION, newsession.get))
    }
  }

  val secretKey = "SECRET IS GOING HERE"
  //UUID.randomUUID().toString
  val sessions = new SessionManager(secretKey)

  def router = staticrouter orElse sessions(dynamicrouter)

}
