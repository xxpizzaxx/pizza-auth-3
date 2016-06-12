package moe.pizza.auth.webapp

import moe.pizza.auth.config.ConfigFile.{AuthConfig, ConfigFile}
import moe.pizza.auth.graphdb.EveMapDb
import moe.pizza.auth.interfaces.{PilotGrader, UserDatabase}
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.webapp.Types.Session2
import moe.pizza.crestapi.CrestApi
import org.http4s.{Uri, Request, Response, Headers, Status, Header}
import org.http4s.headers.Location
import org.scalatest.mock.MockitoSugar
import org.scalatest.{MustMatchers, FlatSpec, FunSpec}
import org.mockito.Mockito._
import org.http4s.{HttpService, _}
import org.http4s.dsl.{Root, _}
import org.http4s.server._
import Types._

/**
  * Created by andi on 03/06/16.
  */
class DynamicRouterSpec extends FlatSpec with MockitoSugar with MustMatchers {

  "DynamicRouter" should "redirect to the CREST login URL when logging in" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    when(config.auth).thenReturn(authconfig)
    when(crest.redirect("login", Webapp.defaultCrestScopes)).thenReturn("http://login.eveonline.com/whatever")

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db))

    val res = app.dynamicWebRouter(Request(uri = Uri.uri("/login")))

    val resp = res.run
    resp.status must equal(Status.TemporaryRedirect)
    resp.headers.iterator.toList must equal(Headers(Location(Uri.uri("http://login.eveonline.com/whatever"))).iterator.toList)
  }
  // pending("rewrite of the middleware") "DynamicRouter" should "create users when they POST at the confirm signup page" in {
    /*
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val pilot = Pilot(Utils.sanitizeUserName("Character Name"),
                  Pilot.Status.unclassified,
                  "Alliance",
                  "Corporation",
                  "Character Name",
                  "none@none",
                  Pilot.OM.createObjectNode(),
                  List.empty[String],
                  List("123:refreshtoken"),
                  List.empty[String])
    val session = new Session2(List.empty, Some(pilot), None)
    when(config.auth).thenReturn(authconfig)

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db))

    val req = Request(method = Method.POST, uri = Uri.uri("/signup/confirm")).withBody(UrlForm(("password" -> "testpassword"), ("email" -> "none@none"))).run
    val req2 = req.withAttribute(SessionManager.HYDRATEDSESSION, session)
    val res = app.dynamicWebRouter(req2)
    val resp = res.run
    resp.status must equal(Status.SeeOther)
    resp.attributes.get(SessionManager.SESSION).get.alerts.length must equal(1)
    resp.attributes.get(SessionManager.SESSION).get.alerts(0) must equal(Alert("success", "Successfully created and signed in as character_name"))
    verify(ud).addUser(pilot, "testpassword")
    */
  //}

}
