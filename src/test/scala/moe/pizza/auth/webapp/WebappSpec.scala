package moe.pizza.auth.webapp

import javax.servlet.http.HttpSession

import moe.pizza.auth.interfaces.UserDatabase
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.webapp.Types.Alert
import moe.pizza.auth.webapp.WebappTestSupports._
import moe.pizza.crestapi.CrestApi
import moe.pizza.crestapi.CrestApi.{VerifyResponse, CallbackResponse}
import moe.pizza.eveapi.{XMLApiResponse, EVEAPI}
import moe.pizza.eveapi.generated.char.CharacterInfo.{Result, Eveapi}
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}
import org.mockito.Mockito.{when, verify, never, reset, times, spy}
import org.mockito.Matchers.{anyString, anyInt}
import spark._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

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
      val usersession = new Types.Session("Terry", List.empty[Types.Alert])
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
      val usersession = new Types.Session("Terry", List(alert))
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
      verify(resp).redirect("https://sisilogin.testeveonline.com/oauth/authorize/?response_type=code&redirect_uri=http://localhost:4567/callback&client_id=f&scope=characterLocationRead&state=login")
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


  "Webapp" should "verify crest callbacks when doing a login" in {
    withPort { port =>
      val crest = mock[CrestApi]
      val w = new Webapp(readTestConfig(), port, Some(crest))
      w.start()
      val handler = resolve(spark.route.HttpMethod.get, "/callback", ACCEPTHTML)
      val req = mock[Request]
      val session = mock[Session]
      when(req.session).thenReturn(session)
      val resp = mock[Response]
      // arguments
      when(req.queryParams("code")).thenReturn("CRESTCODE")
      when(req.queryParams("state")).thenReturn("login")
      when(crest.callback("CRESTCODE")).thenReturn(Future{new CallbackResponse("ACCESSTOKEN", "TYPE", 100, Some("REF"))})
      when(crest.verify("ACCESSTOKEN")).thenReturn(Future{new VerifyResponse(1, "Bob", "ages", "scopes", "bearer", "owner", "eve")})
      val res = handler.handle[String](req, resp)
      verify(req).queryParams("code")
      verify(req).queryParams("state")
      verify(crest).callback("CRESTCODE")
      verify(crest).verify("ACCESSTOKEN")
      val finalsession = new Types.Session("Bob", List(new Alert("success", "Thanks for logging in %s".format("Bob"))))
      verify(session).attribute(Webapp.SESSION, finalsession)
      Spark.stop()
    }
  }

  "Webapp" should "create a pilot when doing a register" in {
    withPort { port =>
      val crest = mock[CrestApi]
      val ud = mock[UserDatabase]
      val eveapi = mock[EVEAPI]
      val char = mock[moe.pizza.eveapi.endpoints.Character]
      when(eveapi.char).thenReturn(char)
      val w = new Webapp(readTestConfig(), port, Some(crest), Some(ud), Some(eveapi))
      w.start()
      val handler = resolve(spark.route.HttpMethod.get, "/callback", ACCEPTHTML)
      val req = mock[Request]
      val session = mock[Session]
      when(req.session).thenReturn(session)
      val resp = mock[Response]
      // arguments
      when(req.queryParams("code")).thenReturn("CRESTCODE")
      when(req.queryParams("state")).thenReturn("register")
      when(crest.callback("CRESTCODE")).thenReturn(Future{new CallbackResponse("ACCESSTOKEN", "TYPE", 100, Some("REF"))})
      when(crest.verify("ACCESSTOKEN")).thenReturn(Future{new VerifyResponse(1, "Bob", "ages", "scopes", "bearer", "owner", "eve")})
      when(crest.refresh("REF")).thenReturn(Future{new CallbackResponse("ACCESSTOKEN", "TYPE", 100, Some("REF"))})
      val bob = new Eveapi("now", new Result(1, "Bob", 0, "who knows", "Dunno", "dunno", "dunno", "male", "bobcorp", 42, "boballiance", 42, null, 0, 0, "nope", 0, 0, 0, "nope", "nope", "nope","nope"), "whenever")
      when(char.CharacterInfo(1)).thenReturn(Future{Try{new XMLApiResponse[Result](DateTime.now(), DateTime.now(), bob.result)}})
      val res = handler.handle[String](req, resp)
      verify(req).queryParams("code")
      verify(req).queryParams("state")
      verify(crest).callback("CRESTCODE")
      verify(crest).verify("ACCESSTOKEN")
      //verify(ud, times(1)).addUser(new Pilot("bob", Pilot.Status.internal, "boballiance", "bobcorp", "Bob", "none@none", Pilot.OM.createObjectNode(), List.empty[String], List("1:REF"), List.empty[String]))
      Spark.stop()
    }
  }

}
