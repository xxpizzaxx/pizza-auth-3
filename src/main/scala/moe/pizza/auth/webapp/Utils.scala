package moe.pizza.auth.webapp

import moe.pizza.auth.models.Pilot
import moe.pizza.auth.webapp.Types.{Session2, Session}
import moe.pizza.auth.webapp.Utils.Alerts.Alerts
import org.http4s.{Response, Request}

import scalaz.concurrent.Task


object Utils {
  object Alerts extends Enumeration {
    type Alerts = Value
    val success = Value("success")
    val info = Value("info")
    val warning = Value("warning")
    val danger = Value("danger")
  }
  implicit class PimpedRequest(r: spark.Request) {
    def flash(level: Alerts, message: String): Unit = {
      val session = getSession
      session match {
        case Some(s) => r.session.attribute(Webapp.SESSION, s.copy(alerts = s.alerts :+ Types.Alert(level.toString, message)))
        case None => ()
      }
    }
    def getSession = Option(r.session.attribute[Types.Session](Webapp.SESSION))
    def setSession(s: Types.Session): Unit = r.session.attribute(Webapp.SESSION, s)
    def getPilot = Option(r.session.attribute[Pilot](Webapp.PILOT))
    def setPilot(p: Pilot): Unit = r.session.attribute(Webapp.PILOT, p)
    def clearAlerts(): Unit = {
      val session = getSession
      session match {
        case Some(s) => r.session.attribute(Webapp.SESSION, s.copy(alerts = List()))
        case None => ()
      }

    }
  }

  implicit class PimpedRequest2(r: Request) {
    def flash(level: Alerts, message: String): Option[Session2] = {
      getSession.map( s =>
        s.copy(alerts = s.alerts :+ Types.Alert(level.toString, message))
      )
    }
    def getSession = r.attributes.get(NewWebapp.SESSION)
    def setSession(s: Types.Session2): Unit = r.attributes.put(NewWebapp.SESSION, s)
    def clearAlerts(): Unit = {
      val session = getSession
      session match {
        case Some(s) => r.setSession(s.copy(alerts = List()))
        case None => ()
      }

    }
  }

  implicit class PimpedResponse(r: Response) {
    def withSession(s: Session2): Response = r.withAttribute(NewWebapp.SESSION, s)
    def withNoSession(): Response = r.copy(attributes = r.attributes.remove(NewWebapp.SESSION))
  }

  implicit class PimpedTaskResponse(r: Task[Response]) {
    def attachSessionifDefined(s: Option[Session2]): Task[Response] =
      r.map(res => s.foldLeft(res){(resp, sess) => resp.withSession(sess)})
    def clearSession(): Task[Response] =
      r.map(res => res.withNoSession())
  }

  def sanitizeUserName(name: String) = name.toLowerCase.replace("'", "").replace(" ", "_")

}
