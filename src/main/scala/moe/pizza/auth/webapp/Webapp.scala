package moe.pizza.auth.webapp

import moe.pizza.auth.config.ConfigFile.ConfigFile
import moe.pizza.auth.interfaces.{PilotGrader, UserDatabase}
import moe.pizza.auth.models.Pilot
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi.{EVEAPI, SyncableFuture}
import org.jboss.netty.handler.codec.http.QueryStringDecoder
import org.jboss.netty.handler.codec.http.multipart.{DefaultHttpDataFactory, HttpPostRequestDecoder}
import util.SparkWebScalaHelpers._
import spark.Spark._
import spark._
import Types._
import Utils._
import scala.collection.JavaConverters._

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
      req.getPilot match {
        case Some(p) => templates.html.base.apply("pizza-auth-3", templates.html.main.apply(), req.getSession, req.getPilot)
        case None => templates.html.base.apply("pizza-auth-3", templates.html.landing.apply(), req.getSession, req.getPilot)
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
            templates.html.base("pizza-auth-3", templates.html.signup(p), req.getSession, req.getPilot)
          case None =>
            req.flash(Alerts.warning, "Unable to find session data, please start signup again")
            resp.redirect("/")
            ""
        }
      }
    })

    post("/signup/confirm", new Route {
      override def handle(request: Request, response: Response): AnyRef = {
        val decoder = new QueryStringDecoder("?"+request.body())
        val params = decoder.getParameters.asScala
        val pilot = request.getPilot.get
        val newemail = params("email").asScala.headOption.getOrElse("none")
        val pilotwithemail = pilot.copy(email = newemail)
        val password = params("password").asScala.head
        val res = ud.addUser(pilotwithemail, password)
        res match {
          case true => request.flash(Alerts.success, s"Successfully created and signed in as ${pilot.uid}")
          case false => request.flash(Alerts.danger, "Unable to create user, please try again later or talk to a sysadmin")
        }
        response.redirect("/")
        ""
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
                // mark it as ineligible if it fell through
                val gradedpilot2 = if (gradedpilot.accountStatus == Pilot.Status.unclassified) {
                  gradedpilot.copy(accountStatus = Pilot.Status.ineligible)
                } else {
                  gradedpilot
                }
                // store it and forward them on
                req.session(true)
                req.setPilot(gradedpilot2)
                val session = new Types.Session(List())
                req.setSession(session)
                resp.redirect("/signup/confirm")
              case Failure(f) =>
                req.flash(Alerts.warning, "Unable to unpack CREST response, please try again later")
                resp.redirect("/")
            }
          case "add" =>
            resp.redirect("/")
          // add it to this user's list of pilots
          case "login" =>
            val uid = Utils.sanitizeUserName(verify.characterName)
            val pilot = ud.getUser(uid)
            pilot match {
              case Some(p) =>
                req.setPilot(p)
                req.setSession(new Types.Session(List()))
                req.flash(Alerts.success, "Thanks for logging in %s".format(verify.characterName))
              case None =>
                req.setSession(new Types.Session(List()))
                req.flash(Alerts.warning, "Unable to find a user associated with that EVE character, please sign up or use another character")
            }
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
