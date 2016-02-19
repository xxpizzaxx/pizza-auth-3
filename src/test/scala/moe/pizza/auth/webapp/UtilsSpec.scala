package moe.pizza.auth.webapp

import moe.pizza.auth.webapp.Types.Alert
import moe.pizza.auth.webapp.Utils.Alerts
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}
import org.mockito.Mockito.{when, verify, never, reset, times}
import org.mockito.Matchers.{anyString, anyInt}
import spark.{Session, Request}

/**
  * Created by Andi on 19/02/2016.
  */
class UtilsSpec extends FlatSpec with MustMatchers with MockitoSugar {

  "pimpedrequest" should "store sessions" in {
    trait mock
    val r = mock[Request]
    val s = mock[Session]
    when(r.session()).thenReturn(s)
    val session = new Types.Session("foo", "bar", "Terry", 1, List.empty[Types.Alert])
    import Utils.PimpedRequest
    // set a session
    r.setSession(session)
    verify(s).attribute(Webapp.SESSION, session)
  }

  "pimpedrequest" should "get sessions" in {
    trait mock
    val r = mock[Request]
    val s = mock[Session]
    when(r.session()).thenReturn(s)
    val session = new Types.Session("foo", "bar", "Terry", 1, List.empty[Types.Alert])
    import Utils.PimpedRequest
    // get a session
    when(s.attribute[Types.Session](Webapp.SESSION)).thenReturn(session)
    r.getSession.get must equal(session)
    verify(s).attribute[Types.Session](Webapp.SESSION)
  }

  "pimpedrequest" should "flash to sessions" in {
    trait mock
    val r = mock[Request]
    val s = mock[Session]
    when(r.session()).thenReturn(s)
    val session = new Types.Session("foo", "bar", "Terry", 1, List.empty[Types.Alert])
    val session2 = new Types.Session("foo", "bar", "Terry", 1, List(Types.Alert("info", "I like turtles")))
    import Utils.PimpedRequest
    // flash to a session
    when(s.attribute[Types.Session](Webapp.SESSION)).thenReturn(session)
    r.flash(Alerts.info, "I like turtles")
    verify(s).attribute(Webapp.SESSION)
    verify(s).attribute(Webapp.SESSION, session.copy(alerts = session.alerts :+ Types.Alert("info", "I like turtles")))
    when(s.attribute[Types.Session](Webapp.SESSION)).thenReturn(session2)
    r.getSession.get.alerts must equal(List(new Alert("info", "I like turtles")))
  }

  "pimpedrequest" should "clear alerts" in {
    trait mock
    val r = mock[Request]
    val s = mock[Session]
    when(r.session()).thenReturn(s)
    val session = new Types.Session("foo", "bar", "Terry", 1, List(Types.Alert("info", "I like turtles")))
    import Utils.PimpedRequest
    // clear a session
    when(s.attribute[Types.Session](Webapp.SESSION)).thenReturn(session)
    r.clearAlerts()
    verify(s).attribute(Webapp.SESSION)
    verify(s).attribute(Webapp.SESSION, session.copy(alerts = List.empty[Types.Alert]))
  }

  "pimpedrequest" should "do nothing if there's no session when you clear alerts or flash" in {
    trait mock
    val r = mock[Request]
    val s = mock[Session]
    when(r.session()).thenReturn(s)
    val session = new Types.Session("foo", "bar", "Terry", 1, List(Types.Alert("info", "I like turtles")))
    import Utils.PimpedRequest
    // clear a session
    when(s.attribute[Types.Session](Webapp.SESSION)).thenReturn(null)
    r.clearAlerts()
    r.flash(Alerts.info, "I like turtles")
    verify(s, times(2)).attribute[Types.Session](Webapp.SESSION)
  }



}
