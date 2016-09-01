package moe.pizza.auth.webapp

import moe.pizza.auth.interfaces.UserDatabase
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.webapp.Types.{HydratedSession, Session2, Session}
import moe.pizza.auth.webapp.Utils.Alerts.Alerts
import org.http4s.{Uri, Response, Request}
import org.http4s.dsl.{Root, _}

import scalaz.concurrent.Task

object Utils {
  object Alerts extends Enumeration {
    type Alerts = Value
    val success = Value("success")
    val info = Value("info")
    val warning = Value("warning")
    val danger = Value("danger")
  }

  implicit class PimpedSession2(s: Session2) {
    def hydrate(u: UserDatabase): HydratedSession = {
      new HydratedSession(s.alerts, s.uid.flatMap(u.getUser), s.signupData)
    }
  }

  implicit class PimpedHydratedSession(hs: HydratedSession) {
    def dehydrate(): Session2 = {
      new Session2(hs.alerts, hs.pilot.map(_.uid), hs.signupData)
    }
  }

  implicit class PimpedRequest(r: Request) {
    def flash(level: Alerts, message: String): Option[HydratedSession] = {
      getSession.map(s =>
        s.copy(alerts = s.alerts :+ Types.Alert(level.toString, message)))
    }
    def getSession = r.attributes.get(SessionManager.HYDRATEDSESSION)
    def setSession(s: Types.HydratedSession): Unit =
      r.attributes.put(SessionManager.HYDRATEDSESSION, s)
    def sessionResponse(
        f: ((HydratedSession, Pilot) => Task[Response]),
        error: String = "You must be signed in to do that"): Task[Response] = {
      (getSession, getSession.flatMap(_.pilot)) match {
        case (Some(s), Some(p)) =>
          f(s, p)
        case _ =>
          TemporaryRedirect(Uri(path = "/"))
      }
    }
  }

  implicit class PimpedResponse(r: Response) {
    def withSession(s: HydratedSession): Response =
      r.withAttribute(SessionManager.HYDRATEDSESSION, s)
    def getSession(): Option[HydratedSession] =
      r.attributes.get(SessionManager.HYDRATEDSESSION)
    def withNoSession(): Response = r.withAttribute(SessionManager.LOGOUT, "")
  }

  implicit class PimpedTaskResponse(r: Task[Response]) {
    def attachSessionifDefined(s: Option[HydratedSession]): Task[Response] =
      r.map(res =>
        s.foldLeft(res) { (resp, sess) =>
          resp.withSession(sess)
      })
    def clearSession(): Task[Response] =
      r.map(res => res.withNoSession())
  }

  def sanitizeUserName(name: String) =
    name.toLowerCase.replace("'", "").replace(" ", "_")

}
