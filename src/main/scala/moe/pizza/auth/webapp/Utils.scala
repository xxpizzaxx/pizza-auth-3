package moe.pizza.auth.webapp

import moe.pizza.auth.models.Pilot
import moe.pizza.auth.webapp.Types.{Session2, Session}
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

  implicit class PimpedRequest(r: Request) {
    def flash(level: Alerts, message: String): Option[Session2] = {
      getSession.map( s =>
        s.copy(alerts = s.alerts :+ Types.Alert(level.toString, message))
      )
    }
    def getSession = r.attributes.get(SessionManager.SESSION)
    def setSession(s: Types.Session2): Unit = r.attributes.put(SessionManager.SESSION, s)
    def clearAlerts(): Unit = {
      val session = getSession
      session match {
        case Some(s) => r.setSession(s.copy(alerts = List()))
        case None => ()
      }
    }
    def sessionResponse(f: ((Session2, Pilot) => Task[Response]), error: String = "You must be signed in to do that" ): Task[Response] = {
      (getSession, getSession.flatMap(_.pilot)) match {
        case (Some(s), Some(p)) =>
          f(s, p)
        case _ =>
          TemporaryRedirect(Uri(path = "/"))
      }
    }
  }

  implicit class PimpedResponse(r: Response) {
    def withSession(s: Session2): Response = r.withAttribute(SessionManager.SESSION, s)
    def withNoSession(): Response = r.withAttribute(SessionManager.LOGOUT, "")
  }

  implicit class PimpedTaskResponse(r: Task[Response]) {
    def attachSessionifDefined(s: Option[Session2]): Task[Response] =
      r.map(res => s.foldLeft(res){(resp, sess) => resp.withSession(sess)})
    def clearSession(): Task[Response] =
      r.map(res => res.withNoSession())
  }

  def sanitizeUserName(name: String) = name.toLowerCase.replace("'", "").replace(" ", "_")

}
