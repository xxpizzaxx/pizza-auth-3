package moe.pizza.auth.webapp

import moe.pizza.auth.config.ConfigFile.ConfigFile
import moe.pizza.auth.interfaces.{PilotGrader, UserDatabase}
import moe.pizza.auth.models.Pilot
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi.{EVEAPI, SyncableFuture}
import util.SparkWebScalaHelpers._
import spark.Spark._
import spark._
import Types._
import Utils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Webapp {
  val SESSION = "session"
  val PILOT = "pilot"
  val defaultCrestScopes = List("characterLocationRead")
}

class Webapp(fullconfig: ConfigFile, graders: PilotGrader, portnumber: Int = 9021, ud: UserDatabase, crestapi: Option[CrestApi] = None, eve: Option[EVEAPI] = None) {

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
      resp.redirect(crest.redirect("login", Webapp.defaultCrestScopes))
    })
    // logout
    get("/logout", (req: Request, resp: Response) => {
      req.session.invalidate()
      resp.redirect("/")
    })

    // signup
    get("/signup", (req: Request, resp: Response) => {
      resp.redirect(crest.redirect("signup", Webapp.defaultCrestScopes))
    })

    get("/signup/confirm", new Route {
      override def handle(req: Request, resp: Response): AnyRef = {
        req.getPilot match {
          case Some(p) =>
            templates.html.base("pizza-auth-3", templates.html.signup(p), req.getSession)
          case None =>
            req.flash(Alerts.warning, "Unable to find session data, please start signup again")
            resp.redirect("/")
            ""
        }
      }
    })


    // callback for when CCP auth sends them back
    get("/callback", new Route {
      override def handle(req: Request, resp: Response): AnyRef = {
        val code = req.queryParams("code")
        val state = req.queryParams("state")
        val callbackresults = crest.callback(code).sync()
        val verify = crest.verify(callbackresults.access_token).sync()
        val r = state match {
          case "signup" =>
            val charinfo = eveapi.eve.CharacterInfo(verify.characterID.toInt).sync()
            val pilot = charinfo.map { ci =>
              val refresh = crest.refresh(callbackresults.refresh_token.get).sync()
              new Pilot(
                Utils.sanitizeUserName(ci.result.characterName),
                Pilot.Status.unclassified,
                ci.result.alliance,
                ci.result.corporation,
                ci.result.characterName,
                "none@none",
                Pilot.OM.createObjectNode(),
                List.empty[String],
                List("%d:%s".format(ci.result.characterID, refresh.refresh_token.get)),
                List.empty[String]
              )

            }
            pilot match {
              case Success(p) =>
                // grade the pilot
                val gradedpilot = p.copy(accountStatus = graders.grade(p))
                // store it and forward them on
                req.session(true)
                req.setPilot(p)
                val session = new Types.Session(verify.characterName, List())
                req.setSession(session)
                resp.redirect("/signup/confirm")
                //ud.get.addUser(p)
                //ud.get.setPassword(p, "whatever")
              case Failure(f) =>
                req.flash(Alerts.warning, "Unable to unpack CREST response, please try again later")
                resp.redirect("/")
            }
          case "add" =>
            resp.redirect("/")
          // add it to this user's list of pilots
          case "login" =>
            val session = new Types.Session(verify.characterName, List(new Alert("success", "Thanks for logging in %s".format(verify.characterName))))
            req.setSession(session)
            // go back to the index since we've just logged in
            resp.redirect("/")
          case _ =>
            resp.redirect("/")
        }
        null
      }
    })
  }
}
