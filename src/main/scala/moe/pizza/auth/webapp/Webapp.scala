package moe.pizza.auth.webapp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import moe.pizza.auth.config.ConfigFile.ConfigFile
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi.{EVEAPI, SyncableFuture}
import util.SparkWebScalaHelpers._
import spark.Spark._
import spark._
import scala.concurrent.duration._
import Types._
import Utils._

import scala.concurrent.ExecutionContext.Implicits.global

object Webapp {
  val SESSION = "session"
  val defaultCrestScopes = List("characterLocationRead")
}

class Webapp(fullconfig: ConfigFile, portnumber: Int = 9021, crestapi: Option[CrestApi] = None) {

  val log = org.log4s.getLogger
  val config = fullconfig.crest

  val crest = crestapi.getOrElse(new CrestApi(baseurl = config.loginUrl, cresturl = config.crestUrl, config.clientID, config.secretKey, config.redirectUrl))
  val eveapi = new EVEAPI()

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
      val session = new Types.Session(callbackresults.access_token, callbackresults.refresh_token.get, verify.characterName, verify.characterID, List(new Alert("success", "Thanks for logging in %s".format(verify.characterName))))
      req.setSession(session)
      // go back to the index since we've just logged in
      resp.redirect("/")
    })
  }
}
