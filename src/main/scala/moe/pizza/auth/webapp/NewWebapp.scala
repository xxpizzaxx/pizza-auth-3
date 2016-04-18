package moe.pizza.auth.webapp

import moe.pizza.auth.config.ConfigFile.ConfigFile
import moe.pizza.auth.graphdb.EveMapDb
import moe.pizza.auth.interfaces.{PilotGrader, UserDatabase}
import moe.pizza.auth.webapp.Types.{Session2, Session}
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi.EVEAPI
import org.http4s.{HttpService, _}
import org.http4s.dsl.{Root, _}
import org.http4s.server._
import org.http4s.server.staticcontent.ResourceService
import org.http4s.server.syntax.ServiceOps
import play.twirl.api.Html
import scala.concurrent.ExecutionContext.Implicits.global
import org.http4s.twirl._
import scalaz._
import Scalaz._

import scalaz.\/-

object NewWebapp {
  val SESSION = AttributeKey[Session2]("SESSION")
  val SESSIONID = AttributeKey[Session2]("SESSIONID")
  val COOKIESESSION = "jwetsession"
  val PILOT = "pilot"
  val defaultCrestScopes = List("characterLocationRead")
}

class NewWebapp(fullconfig: ConfigFile, graders: PilotGrader, portnumber: Int = 9021, ud: UserDatabase, crestapi: Option[CrestApi] = None, eve: Option[EVEAPI] = None, mapper: Option[EveMapDb] = None) {

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



  def staticrouter = staticcontent.resourceService(ResourceService.Config("/static/static/", "/static/"))

  import Utils._

  /*
  def dynamicrouter = HttpService {
    case req@GET -> Root => {
      val newsession = req.flash(Alerts.info, "hi, you have a session")
      Ok(templates.html.base("test-page", templates.html.landing(), newsession, None))
        .attachSessionifDefined(newsession)
    }
  }
  */

  implicit class ConvertableSession2(s: Session2) {
    def toNormalSession = new Session(s.alerts)
  }

  def dynamicrouter = HttpService {
    case req@GET -> Root => {
      req.getSession match {
        case Some(s) =>
          Ok(
            templates.html.base(
              "pizza-auth-3",
              templates.html.main(),
              req.getSession.map(_.toNormalSession),
              req.getSession.flatMap(_.pilot)
            )
          ).attachSessionifDefined(req.getSession.map(_.copy(alerts = List())))
        case None =>
          InternalServerError(templates.html.base("pizza-auth-3", Html("An error occurred with the session handler"), None, None))
      }
    }
    case req@GET -> Root / "login" => {
      Uri.fromString(crest.redirect("login state", NewWebapp.defaultCrestScopes)) match {
        case \/-(url) => TemporaryRedirect(url)
        case _ => InternalServerError("unable to construct url")
      }
    }
    case req@GET -> Root / "logout" => {
      TemporaryRedirect(Uri(path = "/"))
        .clearSession()
    }
  }

  val secretKey = "SECRET IS GOING HERE"
  //UUID.randomUUID().toString
  val sessions = new SessionManager(secretKey)

  def router = staticrouter orElse sessions(dynamicrouter)

}
