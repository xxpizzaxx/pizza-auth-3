package moe.pizza.auth.webapp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import moe.pizza.auth.config.ConfigFile.ConfigFile
import moe.pizza.auth.interfaces.UserDatabase
import moe.pizza.auth.models.Pilot
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi.{EVEAPI, SyncableFuture}
import util.SparkWebScalaHelpers._
import spark.Spark._
import spark._
import scala.concurrent.duration._
import Types._
import Utils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Webapp {
  val SESSION = "session"
  val defaultCrestScopes = List("characterLocationRead")
}

class Webapp(fullconfig: ConfigFile, portnumber: Int = 9021, crestapi: Option[CrestApi] = None, ud: Option[UserDatabase] = None, eve: Option[EVEAPI] = None) {

  val log = org.log4s.getLogger
  val config = fullconfig.crest

  val crest = crestapi.getOrElse(new CrestApi(baseurl = config.loginUrl, cresturl = config.crestUrl, config.clientID, config.secretKey, config.redirectUrl))
  val eveapi = eve.getOrElse(new EVEAPI())

  def start(): Unit = {

    port(portnumber)
    staticFileLocation("/static/")

    // index page
    get("/", (req: Request, resp: Response) => {
      req.getSession match {
        case Some(s) => templates.html.base.apply("pizza-auth-3", templates.html.main.apply(), Some(s))
        case None => templates.html.base.apply("pizza-auth-3", templates.html.landing.apply(), None)
      }
    })
    after("/", (req: Request, resp: Response) => {
      val session = req.getSession
      session match {
        case Some(s) => req.clearAlerts()
        case _ => ()
      }
    })
    // login redirect to use CCP auth
    get("/login", (req: Request, resp: Response) => {
      req.session(true)
      resp.redirect(crest.redirect("", Webapp.defaultCrestScopes))
    })
    // logout
    get("/logout", (req: Request, resp: Response) => {
      req.session.invalidate()
      resp.redirect("/")
    })

    // callback for when CCP auth sends them back
    get("/callback", (req: Request, resp: Response) => {
      val code = req.queryParams("code")
      val state = req.queryParams("state")
      val callbackresults = crest.callback(code).sync()
      val verify = crest.verify(callbackresults.access_token).sync()
      state match {
        case "register" =>
          val charinfo = eveapi.char.CharacterInfo(verify.characterID.toInt).sync()
          val pilot = charinfo.map { ci =>
            val refresh = crest.refresh(callbackresults.refresh_token.get).sync()
            new Pilot(
              Utils.sanitizeUserName(ci.result.name),
              Pilot.Status.internal,
              ci.result.allianceName,
              ci.result.corporationName,
              ci.result.name,
              "none@none",
              Pilot.OM.createObjectNode(),
              List.empty[String],
              List("%d:%s".format(ci.result.characterID, refresh.refresh_token.get)),
              List.empty[String]
            )

          }
          pilot match {
            case Success(p) =>
              ud.get.addUser(p)
              ud.get.setPassword(p, "whatever")
            case Failure(f) =>
              println("oh no")
              println(f)
              ()
          }
        case "add" =>
          // add it to this user's list of pilots
        case "login" =>
          val session = new Types.Session(callbackresults.access_token, callbackresults.refresh_token.get, verify.characterName, verify.characterID, List(new Alert("success", "Thanks for logging in %s".format(verify.characterName))))
          req.setSession(session)
          // go back to the index since we've just logged in
          resp.redirect("/")
      }
      resp.redirect("/")
    })
  }
}
