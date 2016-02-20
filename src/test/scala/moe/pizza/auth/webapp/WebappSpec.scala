package moe.pizza.auth.webapp

import javax.servlet.http.HttpSession

import moe.pizza.auth.webapp.WebappTestSupports._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}
import org.mockito.Mockito.{when, verify, never, reset, times, spy}
import org.mockito.Matchers.{anyString, anyInt}
import spark._

/**
  * Created by Andi on 19/02/2016.
  */
class WebappSpec extends FlatSpec with MustMatchers with MockitoSugar {

  val ACCEPTHTML = "text/html"

  "Webapp" should "serve the landing page" in {
    withPort { port =>
      val w = new Webapp(readTestConfig(), port)
      w.start()
      val handler = resolve(spark.route.HttpMethod.get, "/", ACCEPTHTML)
      val req = mock[Request]
      val session = mock[Session]
      when(req.session()).thenReturn(session)
      when(session.attribute(Webapp.SESSION)).thenReturn(null)
      val resp = mock[Response]
      val res = handler.handle[String](req, resp)
      res.trim must equal(templates.html.base.apply("pizza-auth-3", templates.html.landing.apply(), None).toString().trim)
      val posthandler = resolve(spark.route.HttpMethod.after, "/", ACCEPTHTML)
      val res2 = posthandler.filter[Any](req, resp)
      verify(session, times(2)).attribute[Types.Session](Webapp.SESSION)
      Spark.stop()
    }
  }

  "Webapp" should "serve the main page" in {
    withPort { port =>
      val w = new Webapp(readTestConfig(), port)
      w.start()
      val handler = resolve(spark.route.HttpMethod.get, "/", ACCEPTHTML)
      val req = mock[Request]
      val session = mock[Session]
      val usersession = new Types.Session("foo", "bar", "Terry", 1, List.empty[Types.Alert])
      when(req.session()).thenReturn(session)
      when(session.attribute(Webapp.SESSION)).thenReturn(usersession)
      val resp = mock[Response]
      val res = handler.handle[String](req, resp)
      res.trim must equal(templates.html.base.apply("pizza-auth-3", templates.html.main.apply(), Some(usersession)).toString().trim)
      Spark.stop()
    }
  }

  "Webapp" should "serve the main page with alerts" in {
    withPort { port =>
      val w = new Webapp(readTestConfig(), port)
      w.start()
      val handler = resolve(spark.route.HttpMethod.get, "/", ACCEPTHTML)
      val req = mock[Request]
      val session = mock[Session]
      val alert = Types.Alert("info", "ducks are cool too")
      val usersession = new Types.Session("foo", "bar", "Terry", 1, List(alert))
      when(req.session()).thenReturn(session)
      when(session.attribute(Webapp.SESSION)).thenReturn(usersession)
      val resp = mock[Response]
      val res = handler.handle[String](req, resp)
      res.trim must equal(templates.html.base.apply("pizza-auth-3", templates.html.main.apply(), Some(usersession)).toString().trim)
      // ensure that our alert got shown
      res contains "ducks are cool too" must equal(true)
      // run the post-filter
      val posthandler = resolve(spark.route.HttpMethod.after, "/", ACCEPTHTML)
      val res2 = posthandler.filter[Any](req, resp)
      // make sure it cleared the alerts
      verify(session).attribute(Webapp.SESSION, usersession.copy(alerts = List.empty[Types.Alert]))
      Spark.stop()
    }
  }

  "Webapp" should "redirect to CREST on /login" in {
    withPort { port =>
      val w = new Webapp(readTestConfig(), port)
      w.start()
      val handler = resolve(spark.route.HttpMethod.get, "/login", ACCEPTHTML)
      val req = mock[Request]
      val session = mock[Session]
      when(req.session()).thenReturn(session)
      when(session.attribute(Webapp.SESSION)).thenReturn(null)
      val resp = mock[Response]
      val res = handler.handle[String](req, resp)
      verify(req).session(true)
      verify(resp).redirect("https://sisilogin.testeveonline.com/oauth/authorize/?response_type=code&redirect_uri=http://localhost:4567/callback&client_id=f&scope=characterLocationRead&state=")
      Spark.stop()
    }
  }

  "Webapp" should "clear the session on /logout" in {
    withPort { port =>
      val w = new Webapp(readTestConfig(), port)
      w.start()
      val handler = resolve(spark.route.HttpMethod.get, "/logout", ACCEPTHTML)
      val req = mock[Request]
      val httpsession = mock[HttpSession]
      val session = reflectSession(httpsession)
      when(req.session).thenReturn(session)
      when(session.attribute(Webapp.SESSION)).thenReturn(null)
      val resp = mock[Response]
      val res = handler.handle[String](req, resp)
      verify(req).session()
      verify(resp).redirect("/")
      verify(httpsession).invalidate()
      Spark.stop()
    }
  }

}
