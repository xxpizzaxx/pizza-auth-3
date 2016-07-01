package moe.pizza.auth.webapp

import moe.pizza.auth.config.ConfigFile.{AuthConfig, ConfigFile}
import moe.pizza.auth.graphdb.EveMapDb
import moe.pizza.auth.interfaces.{PilotGrader, UserDatabase}
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.tasks.Update
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
import Utils._

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
  "DynamicRouter" should "update a pilot if I'm in the right groups" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val update = mock[Update]
    when(config.auth).thenReturn(authconfig)
    when(crest.redirect("login", Webapp.defaultCrestScopes)).thenReturn("http://login.eveonline.com/whatever")

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db), updater = Some(update))

    val bob = new Pilot("bob", null, null, null, null, null, null, null, null, null)

    when(ud.getUser("bob")).thenReturn(Some(bob))

    val p = new Pilot(null, null, null, null, null, null, null, List("admin"), null, null)
    val req = Request(uri = Uri.uri("/update/bob"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(p), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    verify(update).updateUser(bob)
    resp.status must equal(Status.Ok)
  }

}
