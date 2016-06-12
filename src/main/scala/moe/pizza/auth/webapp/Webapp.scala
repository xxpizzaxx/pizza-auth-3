package moe.pizza.auth.webapp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import moe.pizza.auth.config.ConfigFile.ConfigFile
import moe.pizza.auth.graphdb.EveMapDb
import moe.pizza.auth.interfaces.{BroadcastService, PilotGrader, UserDatabase}
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.plugins.LocationManager
import moe.pizza.auth.tasks.Update
import moe.pizza.auth.webapp.Types.{HydratedSession, Session2, Session}
import moe.pizza.crestapi.CrestApi
import org.http4s.{HttpService, _}
import org.http4s.dsl.{Root, _}
import org.http4s.server._
import org.http4s.server.staticcontent.ResourceService
import org.http4s.server.syntax.ServiceOps
import org.joda.time.DateTime
import play.twirl.api.Html
import moe.pizza.eveapi._
import scala.concurrent.ExecutionContext.Implicits.global
import org.http4s.twirl._
import scala.concurrent.Future
import scala.util.Try
import scalaz._
import Scalaz._
import scala.util.{Success => TSuccess}
import scala.util.{Failure => TFailure}
import scala.concurrent.duration._
import scala.concurrent.Await
import moe.pizza.crestapi.character.location.Types.Location
import org.slf4j.LoggerFactory


import scalaz.\/-

object Webapp {
  val PILOT = "pilot"
  val defaultCrestScopes = List("characterLocationRead", "characterAccountRead", "fleetRead")
}

class Webapp(fullconfig: ConfigFile,
             graders: PilotGrader,
             portnumber: Int = 9021,
             ud: UserDatabase,
             crestapi: Option[CrestApi] = None,
             eve: Option[EVEAPI] = None,
             mapper: Option[EveMapDb] = None,
             broadcasters: List[BroadcastService] = List.empty[BroadcastService]
               ) {

  val log = LoggerFactory.getLogger(getClass)
  log.info("created Webapp")
  val config = fullconfig.crest
  val groupconfig = fullconfig.auth.groups

  val crest = crestapi.getOrElse(new CrestApi(baseurl = config.loginUrl, cresturl = config.crestUrl, config.clientID, config.secretKey, config.redirectUrl))
  val eveapi = eve.getOrElse(new EVEAPI())
  val map = mapper.getOrElse {
    // make the graph database in the webapp for the MVP version
    val map = new EveMapDb("internal-map")
    // initialise the database
    map.provisionIfRequired()
    map
  }

  val updater = new Update(crest, eveapi, graders)

  // used for serializing JSON responses, for now
  val OM = new ObjectMapper()
  OM.registerModules(DefaultScalaModule)

  case class PlayerWithLocation(name: String, system: String, station: Option[String])

  def staticrouter = staticcontent.resourceService(ResourceService.Config("/static/static/", "/static/"))

  import Utils._

  implicit class RichHydratedSession(hs: HydratedSession) {
    def toNormalSession = new Session(hs.alerts)
     def updatePilot: HydratedSession = {
       hs.copy(pilot = ud.getUser(hs.pilot.get.uid))
     }
  }

  def getJabberServer(u: Pilot): String = u.accountStatus match {
    case Pilot.Status.internal => fullconfig.auth.domain
    case Pilot.Status.ally => s"allies.${fullconfig.auth.domain}"
    case Pilot.Status.ineligible => s"public.${fullconfig.auth.domain}"
    case _ => "none"
  }

  def dynamicWebRouter = HttpService {
    case req@GET -> Root => {
      req.getSession match {
        case Some(s) =>
          s.pilot match {
            case Some(pilot) =>
              Ok(
                templates.html.base(
                  "pizza-auth-3",
                  templates.html.main(pilot, getJabberServer(pilot)),
                  req.getSession.map(_.toNormalSession),
                  req.getSession.flatMap(_.pilot)
                )
              ).attachSessionifDefined(req.getSession.map(_.copy(alerts = List())))
            case None =>
              Ok(templates.html.base(
                "pizza-auth-3",
                templates.html.landing(),
                req.getSession.map(_.toNormalSession),
                None
              )).attachSessionifDefined(req.getSession.map(_.copy(alerts = List())))
          }
        case None =>
          InternalServerError(templates.html.base("pizza-auth-3", Html("An error occurred with the session handler"), None, None))
      }
    }
    case req@GET -> Root / "login" => {
      Uri.fromString(crest.redirect("login", Webapp.defaultCrestScopes)) match {
        case \/-(url) => TemporaryRedirect(url)
        case _ => InternalServerError("unable to construct url")
      }
    }
    case req@GET -> Root / "signup" => {
      Uri.fromString(crest.redirect("signup", Webapp.defaultCrestScopes)) match {
        case \/-(url) => TemporaryRedirect(url)
        case _ => InternalServerError("unable to construct url")
      }
    }
    case req@GET -> Root / "logout" => {
      TemporaryRedirect(Uri(path = "/"))
        .clearSession()
    }

    case req@GET -> Root / "signup" / "confirm" => {
      log.info("following route for GET signup/confirm")
      log.info(s"${req.getSession}")
      req.getSession.flatMap(_.pilot) match {
        case Some(p) =>
          Ok(templates.html.base("pizza-auth-3", templates.html.signup(p), req.getSession.map(_.toNormalSession), req.getSession.flatMap(_.pilot)))
        case None =>
          TemporaryRedirect(Uri(path = "/"))
      }
    }

    case req@POST -> Root / "signup" / "confirm" => {
      log.info("signup confirm called")
      val res = Try {
        req.getSession.flatMap(_.pilot) match {
          case Some(p) =>
            req.decode[UrlForm] { data =>
              val newemail = data.getFirstOrElse("email", "none")
              val pilotwithemail = p.copy(email = newemail)
              val password = data.getFirst("password").get
              log.info(s"signing up ${pilotwithemail.uid}")
              log.info(OM.writeValueAsString(pilotwithemail))
              val res = ud.addUser(pilotwithemail, password)
              log.info(s"$res")
              SeeOther(Uri(path = "/"))
                .attachSessionifDefined(
                  req.flash(Alerts.success, s"Successfully created and signed in as ${p.uid}").map {
                    _.updatePilot
                  }
                )
            }
          case None =>
            SeeOther(Uri(path = "/"))
        }
      }
      println(res)
      res.get
    }

    case req@GET -> Root / "groups" => {
      req.getSession.map(_.updatePilot).flatMap(_.pilot) match {
        case Some(p) =>
          val groups = fullconfig.auth.groups
          Ok(templates.html.base("pizza-auth-3", templates.html.groups(p, groups.closed, groups.open), req.getSession.map(_.toNormalSession), req.getSession.flatMap(_.pilot)))
            .attachSessionifDefined(req.getSession.map(_.copy(alerts = List())))
        case None =>
          TemporaryRedirect(Uri(path = "/"))
      }
    }

    case req@GET -> Root / "ping" => {
      req.getSession.map(_.updatePilot).flatMap(_.pilot) match {
        case Some(p) =>
          p.getGroups contains "ping" match {
            case true =>
              Ok(templates.html.base("pizza-auth-3", templates.html.ping(), req.getSession.map(_.toNormalSession), req.getSession.flatMap(_.pilot)))
                .attachSessionifDefined(req.getSession.map(_.copy(alerts = List())))
            case false => TemporaryRedirect(Uri(path = "/")).attachSessionifDefined(req.flash(Alerts.warning, "You must be in the ping group to access that resource"))
          }
        case None =>
          TemporaryRedirect(Uri(path = "/"))
      }
    }

    case req@GET -> Root / "locations" => {
      req.getSession.map(_.updatePilot).flatMap(_.pilot) match {
        case Some(p) =>
          p.getGroups contains "ping" match {
            case true =>
              val locations = LocationManager.locateUsers(crest)(ud.getUsers("accountStatus=Internal")).flatten.map { kv =>
                Try{
                  (kv._1, Await.result(kv._2, 10 seconds))
                }
              }
              log.info(locations.toString)
              val res = locations.map(_.toOption).flatten.filter{x => x._2.solarSystem.isDefined}.map{x => PlayerWithLocation(x._1.characterName, x._2.solarSystem.get.name, x._2.station.map{_.name})}
              Ok(OM.writeValueAsString(res))
            case false => TemporaryRedirect(Uri(path = "/")).attachSessionifDefined(req.flash(Alerts.warning, "You must be in the ping group to access that resource"))
          }
        case None =>
          TemporaryRedirect(Uri(path = "/"))
      }
    }
    case req@POST -> Root / "ping" / "global" => {
       req.getSession.map(_.updatePilot).flatMap(_.pilot) match {
        case Some(p) =>
          p.getGroups contains "ping" match {
            case true =>
              req.decode[UrlForm] { form =>
                val flags = List("internal", "ally", "public")
                val groups = flags.flatMap{g => form.getFirst(g).map(_ => g.capitalize)}
                log.info(s"sending a ping to groups ${groups}")
                val users = groups.map( name => (name, ud.getUsers(s"accountStatus=${name}")))
                val message = form.getFirstOrElse("message", "This message is left intentionally blank.")
                val total = users.map { kv =>
                  val (label, us) = kv
                  val templatedMessage = templates.txt.broadcast(message, label, p.uid, DateTime.now())
                  val totals = broadcasters.map { b=>
                    b.sendAnnouncement(templatedMessage.toString(), p.uid, us)
                  }
                  Await.result(Future.sequence(totals), 2 seconds)
                }.map{_.sum}.sum
                SeeOther(Uri(path = "/ping")).attachSessionifDefined(req.flash(Alerts.info, s"Message sent to ${total} users."))
              }
            case false =>
              SeeOther(Uri(path = "/")).attachSessionifDefined(req.flash(Alerts.warning, "You must be in the ping group to access that resource"))
          }
        case None =>
          SeeOther(Uri(path = "/"))
      }

    }

    case req@GET -> Root / "groups" / "apply" / group => {
      val goback = TemporaryRedirect(Uri(path = "/groups"))
      req.sessionResponse { (s: HydratedSession, p: Pilot) =>
        // TODO extend for public users
        group match {
          case g if groupconfig.open.contains(g) =>
            ud.updateUser(p.copy(authGroups = p.authGroups :+ group)) match {
              case true =>
                goback.attachSessionifDefined(req.flash(Alerts.success, s"Joined $group").map(_.updatePilot))
              case false =>
                goback.attachSessionifDefined(req.flash(Alerts.warning, s"Unable to join $group"))
            }
          case g if groupconfig.closed.contains(g) =>
            ud.updateUser(p.copy(authGroups = p.authGroups :+ s"$group-pending" )) match {
              case true =>
                goback.attachSessionifDefined(req.flash(Alerts.success, s"Applied to $group").map(_.updatePilot))
              case false =>
                goback.attachSessionifDefined(req.flash(Alerts.warning, s"Unable to join $group"))
            }
          case _ =>
            goback.attachSessionifDefined(req.flash(Alerts.warning, s"Unable to find a group named $group"))
        }
      }
    }

    case req@GET -> Root / "groups" / "remove" / group => {
      val goback = TemporaryRedirect(Uri(path = "/groups"))
      req.sessionResponse { (s: HydratedSession, p: Pilot) =>
        // TODO extend for public users
        group match {
          case g if p.authGroups.contains(g) =>
            ud.updateUser(p.copy(authGroups = p.authGroups.filter(_ != group) )) match {
              case true =>
                goback.attachSessionifDefined(req.flash(Alerts.success, s"Left $group").map(_.updatePilot))
              case false =>
                goback.attachSessionifDefined(req.flash(Alerts.warning, s"Unable to join $group"))
            }
          case _ =>
            goback.attachSessionifDefined(req.flash(Alerts.warning, s"You are not in a group named $group"))
        }
      }
    }


    case req@GET -> Root / "update" / username =>
      log.info(s"update route called for ${username}")
      req.getSession.flatMap(_.pilot) match {
        case Some(p) =>
          p.getGroups contains "ping" match {
            case true =>
              ud.getUser(username) match {
                case Some(u) =>
                  val updatedpilot = updater.updateUser(u)
                  ud.updateUser(updatedpilot)
                  Ok(OM.writeValueAsString(updatedpilot))
                case None =>
                  NotFound()
              }
            case false => TemporaryRedirect(Uri(path = "/")).attachSessionifDefined(req.flash(Alerts.warning, "You must be in the ping group to access that resource"))
          }
        case None =>
          TemporaryRedirect(Uri(path = "/"))
      }


    case req@GET -> Root / "callback" => {
      val code = req.params("code")
      val state = req.params("state")
      val callbackresults = crest.callback(code).sync()
      val verify = crest.verify(callbackresults.access_token).sync()
      state match {
        case "signup" =>
          log.info(s"signup route in callback for ${verify.characterName}")
          val charinfo = eveapi.eve.CharacterInfo(verify.characterID.toInt)
          val pilot = charinfo.map { ci =>
            val refresh = crest.refresh(callbackresults.refresh_token.get).sync()
            ci match {
              case Right(r) =>
                // has an alliance
                new Pilot(
                  Utils.sanitizeUserName(r.result.characterName),
                  Pilot.Status.unclassified,
                  r.result.alliance,
                  r.result.corporation,
                  r.result.characterName,
                  "none@none",
                  Pilot.OM.createObjectNode(),
                  List.empty[String],
                  List("%d:%s".format(r.result.characterID, refresh.refresh_token.get)),
                  List.empty[String]
                )
              case Left(l) =>
                // does not have an alliance
                new Pilot(
                  Utils.sanitizeUserName(l.result.characterName),
                  Pilot.Status.unclassified,
                  "",
                  l.result.corporation,
                  l.result.characterName,
                  "none@none",
                  Pilot.OM.createObjectNode(),
                  List.empty[String],
                  List("%d:%s".format(l.result.characterID, refresh.refresh_token.get)),
                  List.empty[String]
                )

            }
          }
          Try {
            pilot.sync()
          } match {
            case TSuccess(p) =>
              log.info(s"pilot has been read out of XML API: ${p}")
              // grade the pilot
              val gradedpilot = p.copy(accountStatus = graders.grade(p))
              log.info(s"pilot has been graded: ${p}")
              // mark it as ineligible if it fell through
              val gradedpilot2 = if (gradedpilot.accountStatus == Pilot.Status.unclassified) {
                log.info("marking pilot as Ineligible")
                gradedpilot.copy(accountStatus = Pilot.Status.ineligible)
              } else {
                log.info("not marking pilot as Ineligible")
                gradedpilot
              }
              log.info("trying to redirect back to signup confirm")
              log.info(s"session: ${req.getSession}")
              // store it and forward them on
              TemporaryRedirect(Uri(path = "/signup/confirm")).attachSessionifDefined(
                req.getSession.map(_.copy(pilot = Some(gradedpilot)))
              )
            case TFailure(f) =>
              log.info(s"failure when grading pilot, redirecting back ${f.toString}")
              val newsession = req.flash(Alerts.warning, "Unable to unpack CREST response, please try again later")
              TemporaryRedirect(Uri(path = "/")).attachSessionifDefined(newsession)
          }
        case "add" =>
          TemporaryRedirect(Uri(path = "/"))
        case "login" =>
          val uid = Utils.sanitizeUserName(verify.characterName)
          val pilot = ud.getUser(uid)
          val session = pilot match {
            case Some(p) =>
              req.flash(Alerts.success, "Thanks for logging in %s".format(verify.characterName))
                .map(_.copy(pilot = Some(p)))
            case None =>
              req.flash(Alerts.warning,
                "Unable to find a user associated with that EVE character, please sign up or use another character")
          }
          TemporaryRedirect(Uri(path = "/")).attachSessionifDefined(session)
        case _ =>
          TemporaryRedirect(Uri(path = "/"))
      }
    }
  }

  val secretKey = "SECRET IS GOING HERE"
  //UUID.randomUUID().toString
  val sessions = new SessionManager(secretKey, ud)

  def router = staticrouter orElse sessions(dynamicWebRouter)

}
