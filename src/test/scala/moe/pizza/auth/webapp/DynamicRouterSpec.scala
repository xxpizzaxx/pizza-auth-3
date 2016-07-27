package moe.pizza.auth.webapp

import moe.pizza.auth.config.ConfigFile.{AuthConfig, ConfigFile}
import moe.pizza.auth.graphdb.EveMapDb
import moe.pizza.auth.interfaces.{BroadcastService, PilotGrader, UserDatabase}
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.tasks.Update
import moe.pizza.auth.webapp.Types.Session2
import moe.pizza.crestapi.CrestApi
import org.http4s.{Header, Headers, Request, Response, Status, Uri}
import org.http4s.headers.Location
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, FunSpec, MustMatchers}
import org.mockito.Mockito._
import org.http4s.{HttpService, _}
import org.http4s.dsl.{Root, _}
import org.http4s.server._
import Types._
import Utils._
import org.http4s.util.CaseInsensitiveString

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


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
  "DynamicRouter" should "render the main page" in {
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

    val p = new Pilot("bob", null, null, null, "Bob McName", null, null, List("admin"), null, null)
    val req = Request(uri = Uri.uri("/"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(p), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.Ok)
    val bodytxt = EntityDecoder.decodeString(resp)(Charset.`UTF-8`).run
    // should show the currently logged in user, and a logout button
    assert(bodytxt contains "/logout")
    assert(bodytxt contains "Bob McName")
  }
  "DynamicRouter's ping page" should "turn users away without the ping group" in {
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

    val bob = new Pilot("bob", null, null, null, null, null, null, List.empty[String], null, null)

    when(ud.getUser("bob")).thenReturn(Some(bob))

    val req = Request(uri = Uri.uri("/ping"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.TemporaryRedirect)
    resp.headers.get(CaseInsensitiveString("location")).get.value must equal("/")
  }
  "DynamicRouter's ping page" should "render if they have the ping group" in {
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

    val bob = new Pilot("bob", null, null, null, null, null, null, List("ping"), null, null)

    when(ud.getUser("bob")).thenReturn(Some(bob))

    val req = Request(uri = Uri.uri("/ping"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.Ok)
    val bodytxt = EntityDecoder.decodeString(resp)(Charset.`UTF-8`).run
    assert(bodytxt contains "/logout")
  }
  "DynamicRouter's ping page" should "send global pings" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val update = mock[Update]
    val broadcaster = new BroadcastService {
      override def sendAnnouncement(msg: String, from: String, to: List[Pilot]): Future[Int] = Future{to.size}
    }
    when(config.auth).thenReturn(authconfig)
    when(crest.redirect("login", Webapp.defaultCrestScopes)).thenReturn("http://login.eveonline.com/whatever")

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db), updater = Some(update), broadcasters = List(broadcaster))

    val bob = new Pilot("bob", null, null, null, null, null, null, List("ping"), null, null)

    when(ud.getUser("bob")).thenReturn(Some(bob))
    when(ud.getUsers("accountStatus=Internal")).thenReturn(List(bob))

    val req = Request(method=Method.POST, uri = Uri.uri("/ping/global"),body=UrlForm.entityEncoder.toEntity(UrlForm("message"->"test message","internal"->"on")).run.body)
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.getSession.get.alerts.head.content must equal("Message sent to 1 users.")
  }
}
