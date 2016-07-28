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
import moe.pizza.crestapi.CrestApi.{CallbackResponse, VerifyResponse}
import moe.pizza.eveapi.EVEAPI
import org.http4s.util.CaseInsensitiveString
import moe.pizza.eveapi.generated.eve
import moe.pizza.eveapi.XMLApiResponse
import org.joda.time.DateTime
import org.mockito.Matchers._

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
  "DynamicRouter" should "redirect to the CREST login URL when signing up" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    when(config.auth).thenReturn(authconfig)
    when(crest.redirect("signup", Webapp.defaultCrestScopes)).thenReturn("http://login.eveonline.com/whatever2")

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db))

    val res = app.dynamicWebRouter(Request(uri = Uri.uri("/signup")))

    val resp = res.run
    resp.status must equal(Status.TemporaryRedirect)
    resp.headers.iterator.toList must equal(Headers(Location(Uri.uri("http://login.eveonline.com/whatever2"))).iterator.toList)
  }
  "DynamicRouter" should "accept a redirect back from CREST to move the user along in signing up" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    when(config.auth).thenReturn(authconfig)
    when(crest.redirect("signup", Webapp.defaultCrestScopes)).thenReturn("http://login.eveonline.com/whatever2")
    when(crest.callback("codegoeshere")).thenReturn(Future{CallbackResponse("access_token", "bearer", 1000, Some("refresh_token"))})
    val verify = VerifyResponse(103, "bob mcbobface", "some time", "scopes", "token type", "owner hash", "eve online")
    when(crest.verify("access_token")).thenReturn(Future{verify})

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db))

    val p = new Pilot(null, null, null, null, null, null, null, List("admin"), null, null)
    val req = Request(uri = Uri.uri("/callback").withQueryParam("code", "codegoeshere").withQueryParam("state", "signup"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(p), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.TemporaryRedirect)
    resp.getSession.get.signupData.get.refresh must equal("refresh_token")
    resp.getSession.get.signupData.get.verify must equal(verify)
  }
  "DynamicRouter" should "populate the signup page with the user's data" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = new PilotGrader {
      def grade(p: Pilot): Pilot.Status.Value = {Pilot.Status.internal}
    }
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val eveapi = mock[EVEAPI]
    val innereveapi = mock[moe.pizza.eveapi.endpoints.Eve]
    when(eveapi.eve).thenReturn(innereveapi)
    when(config.auth).thenReturn(authconfig)
    val verify = VerifyResponse(103, "bob mcbobface", "some time", "scopes", "token type", "owner hash", "eve online")
    when(crest.verify("access_token")).thenReturn(Future{verify})
    when(crest.refresh("refresh_token")).thenReturn(Future{new CallbackResponse("access_token", "token", 100, Some("refresh_token"))})
    val char = new eve.CharacterInfo.Result(103, "bob mcbobface", "caldari", 1, "whatever", 1, "whatever2", 104, "bobcorp", "now", 105, "boballiance", "now", 4.2, new eve.CharacterInfo.Rowset() )
    val charReturnValue: Either[XMLApiResponse[eve.CharacterInfo2.Result], XMLApiResponse[eve.CharacterInfo.Result]] = Right(new XMLApiResponse(DateTime.now(), DateTime.now(), char))
    when(innereveapi.CharacterInfo(103)).thenReturn(Future{charReturnValue})

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db), eve = Some(eveapi))

    val signupData = new SignupData(verify, "refresh_token")

    val p = new Pilot(null, Pilot.Status.unclassified, null, null, null, null, null, List("admin"), null, null)
    val req = Request(
      method = Method.GET,
      uri = Uri.uri("/signup/confirm")
    )
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(p), Some(signupData))))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.Ok)
    val session = resp.getSession
    val bodytxt = EntityDecoder.decodeString(resp)(Charset.`UTF-8`).run
    assert(bodytxt contains "Internal")
    assert(bodytxt contains "boballiance")
  }
  "DynamicRouter" should "populate the signup page with the user's data even if they have no alliance" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = new PilotGrader {
      def grade(p: Pilot): Pilot.Status.Value = {Pilot.Status.internal}
    }
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val eveapi = mock[EVEAPI]
    val innereveapi = mock[moe.pizza.eveapi.endpoints.Eve]
    when(eveapi.eve).thenReturn(innereveapi)
    when(config.auth).thenReturn(authconfig)
    val verify = VerifyResponse(103, "bob mcbobface", "some time", "scopes", "token type", "owner hash", "eve online")
    when(crest.verify("access_token")).thenReturn(Future{verify})
    when(crest.refresh("refresh_token")).thenReturn(Future{new CallbackResponse("access_token", "token", 100, Some("refresh_token"))})
    val char = new eve.CharacterInfo2.Result(103, "bob mcbobface", "caldari", 1, "whatever", 1, "whatever2", 104, "bobcorp", "now", 4.2, new eve.CharacterInfo2.Rowset() )
    val charReturnValue: Either[XMLApiResponse[eve.CharacterInfo2.Result], XMLApiResponse[eve.CharacterInfo.Result]] = Left(new XMLApiResponse(DateTime.now(), DateTime.now(), char))
    when(innereveapi.CharacterInfo(103)).thenReturn(Future{charReturnValue})

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db), eve = Some(eveapi))

    val signupData = new SignupData(verify, "refresh_token")

    val p = new Pilot(null, Pilot.Status.unclassified, null, null, null, null, null, List("admin"), null, null)
    val req = Request(
      method = Method.GET,
      uri = Uri.uri("/signup/confirm")
    )
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(p), Some(signupData))))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.Ok)
    val session = resp.getSession
    val bodytxt = EntityDecoder.decodeString(resp)(Charset.`UTF-8`).run
    assert(bodytxt contains "Internal")
    assert(bodytxt contains "bobcorp")
  }
  "DynamicRouter" should "create users when they sign up" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = new PilotGrader {
      def grade(p: Pilot): Pilot.Status.Value = {Pilot.Status.internal}
    }
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val eveapi = mock[EVEAPI]
    val innereveapi = mock[moe.pizza.eveapi.endpoints.Eve]
    when(eveapi.eve).thenReturn(innereveapi)
    when(config.auth).thenReturn(authconfig)
    val verify = VerifyResponse(103, "bob mcbobface", "some time", "scopes", "token type", "owner hash", "eve online")
    when(crest.verify("access_token")).thenReturn(Future{verify})
    when(crest.refresh("refresh_token")).thenReturn(Future{new CallbackResponse("access_token", "token", 100, Some("refresh_token"))})
    val char = new eve.CharacterInfo2.Result(103, "bob mcbobface", "caldari", 1, "whatever", 1, "whatever2", 104, "bobcorp", "now", 4.2, new eve.CharacterInfo2.Rowset() )
    val charReturnValue: Either[XMLApiResponse[eve.CharacterInfo2.Result], XMLApiResponse[eve.CharacterInfo.Result]] = Left(new XMLApiResponse(DateTime.now(), DateTime.now(), char))
    when(innereveapi.CharacterInfo(103)).thenReturn(Future{charReturnValue})

    when(ud.addUser(anyObject(), anyString())).thenReturn(true) // accept anything

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db), eve = Some(eveapi))

    val signupData = new SignupData(verify, "refresh_token")

    val p = new Pilot(null, Pilot.Status.unclassified, null, null, null, null, null, List("admin"), null, null)
    val req = Request(
      method = Method.POST,
      uri = Uri.uri("/signup/confirm"),
      body = UrlForm.entityEncoder.toEntity(UrlForm("email"->"bob@bobcorp.corp","password"->"fakepassword")).run.body
    )
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(p), Some(signupData))))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.SeeOther)
    val session = resp.getSession
    session.get.alerts.head.content must equal("Successfully created and signed in as bob_mcbobface")
  }
  "DynamicRouter" should "create users when they sign up with an alliance" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = new PilotGrader {
      def grade(p: Pilot): Pilot.Status.Value = {Pilot.Status.internal}
    }
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val eveapi = mock[EVEAPI]
    val innereveapi = mock[moe.pizza.eveapi.endpoints.Eve]
    when(eveapi.eve).thenReturn(innereveapi)
    when(config.auth).thenReturn(authconfig)
    val verify = VerifyResponse(103, "bob mcbobface", "some time", "scopes", "token type", "owner hash", "eve online")
    when(crest.verify("access_token")).thenReturn(Future{verify})
    when(crest.refresh("refresh_token")).thenReturn(Future{new CallbackResponse("access_token", "token", 100, Some("refresh_token"))})
    val char = new eve.CharacterInfo.Result(103, "bob mcbobface", "caldari", 1, "whatever", 1, "whatever2", 104, "bobcorp", "now", 105, "boballiance", "now", 4.2, new eve.CharacterInfo.Rowset() )
    val charReturnValue: Either[XMLApiResponse[eve.CharacterInfo2.Result], XMLApiResponse[eve.CharacterInfo.Result]] = Right(new XMLApiResponse(DateTime.now(), DateTime.now(), char))
    when(innereveapi.CharacterInfo(103)).thenReturn(Future{charReturnValue})

    when(ud.addUser(anyObject(), anyString())).thenReturn(true) // accept anything

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db), eve = Some(eveapi))

    val signupData = new SignupData(verify, "refresh_token")

    val p = new Pilot(null, Pilot.Status.unclassified, null, null, null, null, null, List("admin"), null, null)
    val req = Request(
      method = Method.POST,
      uri = Uri.uri("/signup/confirm"),
      body = UrlForm.entityEncoder.toEntity(UrlForm("email"->"bob@bobcorp.corp","password"->"fakepassword")).run.body
    )
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(p), Some(signupData))))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.SeeOther)
    val session = resp.getSession
    session.get.alerts.head.content must equal("Successfully created and signed in as bob_mcbobface")
  }
  "DynamicRouter" should "accept a redirect back from CREST to log in" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    when(config.auth).thenReturn(authconfig)
    when(crest.redirect("login", Webapp.defaultCrestScopes)).thenReturn("http://login.eveonline.com/whatever2")
    when(crest.callback("codegoeshere")).thenReturn(Future{CallbackResponse("access_token", "bearer", 1000, Some("refresh_token"))})
    val verifyR = VerifyResponse(103, "bob mcbobface", "some time", "scopes", "token type", "owner hash", "eve online")
    when(crest.verify("access_token")).thenReturn(Future{verifyR})
    val p = new Pilot(null, null, null, null, null, null, null, List("admin"), null, null)
    when(ud.getUser("bob_mcbobface")).thenReturn(Some(p))

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db))

    val req = Request(uri = Uri.uri("/callback").withQueryParam("code", "codegoeshere").withQueryParam("state", "login"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(p), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.TemporaryRedirect)
    val session = resp.getSession
    verify(ud).getUser("bob_mcbobface")
    session.get.alerts.head.content must equal("Thanks for logging in bob mcbobface")
  }
  "DynamicRouter" should "accept a redirect back from CREST to log in, and copy if they don't exist" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    when(config.auth).thenReturn(authconfig)
    when(crest.redirect("login", Webapp.defaultCrestScopes)).thenReturn("http://login.eveonline.com/whatever2")
    when(crest.callback("codegoeshere")).thenReturn(Future{CallbackResponse("access_token", "bearer", 1000, Some("refresh_token"))})
    val verify = VerifyResponse(103, "bob mcbobface", "some time", "scopes", "token type", "owner hash", "eve online")
    when(crest.verify("access_token")).thenReturn(Future{verify})
    val p = new Pilot(null, null, null, null, null, null, null, List("admin"), null, null)
    when(ud.getUser("bob_mcbobface")).thenReturn(None)

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db))

    val req = Request(uri = Uri.uri("/callback").withQueryParam("code", "codegoeshere").withQueryParam("state", "login"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(p), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.TemporaryRedirect)
    val session = resp.getSession
    session.get.alerts.head.content must equal("Unable to find a user associated with that EVE character, please sign up or use another character")
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
  "DynamicRouter" should "render the landing page" in {
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

    val req = Request(uri = Uri.uri("/"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], None, None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.Ok)
    val bodytxt = EntityDecoder.decodeString(resp)(Charset.`UTF-8`).run
    // should show the currently logged in user, and a logout button
    assert(bodytxt contains "/signup")
    assert(bodytxt contains "/login")
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
  "DynamicRouter's ping page" should "send group pings" in {
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

    val bob = new Pilot("bob", null, null, null, null, null, null, List("ping","coolpeople"), null, null)

    when(ud.getUser("bob")).thenReturn(Some(bob))
    val group = "coolpeople"
    when(ud.getUsers(s"|(authgroup=${group})(corporation=${group})(alliance=${group})")).thenReturn(List(bob))

    val req = Request(method=Method.POST, uri = Uri.uri("/ping/group"),body=UrlForm.entityEncoder.toEntity(UrlForm("message"->"test message","group"->"coolpeople")).run.body)
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.getSession.get.alerts.head.content must equal("Message sent to 1 users in group coolpeople.")

    verify(ud).getUsers(s"|(authgroup=${group})(corporation=${group})(alliance=${group})")
  }
  "DynamicRouter's group admin page" should "turn users away without the admin group" in {
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

    val req = Request(uri = Uri.uri("/groups/admin"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.TemporaryRedirect)
    resp.headers.get(CaseInsensitiveString("location")).get.value must equal("/")
  }
  "DynamicRouter's group admin page" should "render if they have the admin group" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val update = mock[Update]
    when(config.auth).thenReturn(authconfig)
    when(crest.redirect("login", Webapp.defaultCrestScopes)).thenReturn("http://login.eveonline.com/whatever")
    val bob = new Pilot("bob", null, null, null, null, null, null, List("admin", "dota-pending", "timerboard-pending"), null, null)
    when(ud.getAllUsers()).thenReturn(Seq(bob))

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db), updater = Some(update))


    when(ud.getUser("bob")).thenReturn(Some(bob))

    val req = Request(uri = Uri.uri("/groups/admin"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.Ok)
    val bodytxt = EntityDecoder.decodeString(resp)(Charset.`UTF-8`).run
    assert(bodytxt contains "/logout")
    assert(bodytxt contains "bob applied to dota")
    assert(bodytxt contains "bob applied to timerboard")
  }
  "DynamicRouter's group admin page" should "accept people to groups" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val update = mock[Update]
    when(config.auth).thenReturn(authconfig)
    when(crest.redirect("login", Webapp.defaultCrestScopes)).thenReturn("http://login.eveonline.com/whatever")
    val bob = new Pilot("bob", null, null, null, null, null, null, List("admin", "dota-pending", "timerboard-pending"), null, null)
    when(ud.getAllUsers()).thenReturn(Seq(bob))

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db), updater = Some(update))


    when(ud.getUser("bob")).thenReturn(Some(bob))
    when(ud.updateUser(anyObject())).thenReturn(true)

    val req = Request(uri = Uri.uri("/groups/admin/approve/bob/dota"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.getSession.get.alerts.head.content must equal("Accepted bob into dota")
    verify(ud).updateUser(bob.copy(authGroups = List("dota", "admin", "timerboard-pending")))
  }
  "DynamicRouter's group admin page" should "deny people from groups" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val update = mock[Update]
    when(config.auth).thenReturn(authconfig)
    when(crest.redirect("login", Webapp.defaultCrestScopes)).thenReturn("http://login.eveonline.com/whatever")
    val bob = new Pilot("bob", null, null, null, null, null, null, List("admin", "dota-pending", "timerboard-pending"), null, null)
    when(ud.getAllUsers()).thenReturn(Seq(bob))

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db), updater = Some(update))


    when(ud.getUser("bob")).thenReturn(Some(bob))
    when(ud.updateUser(anyObject())).thenReturn(true)

    val req = Request(uri = Uri.uri("/groups/admin/deny/bob/dota"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res = app.dynamicWebRouter(reqwithsession)

    val resp = res.run
    resp.getSession.get.alerts.head.content must equal("Denied bob membership of dota")
    verify(ud).updateUser(bob.copy(authGroups = List("admin", "timerboard-pending")))
  }
  "DynamicRouter's group admin page" should "cope with bad accepts and denies" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val update = mock[Update]
    when(config.auth).thenReturn(authconfig)
    when(crest.redirect("login", Webapp.defaultCrestScopes)).thenReturn("http://login.eveonline.com/whatever")
    val bob = new Pilot("bob", null, null, null, null, null, null, List("admin", "dota-pending", "timerboard-pending"), null, null)
    when(ud.getAllUsers()).thenReturn(Seq(bob))

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db), updater = Some(update))


    when(ud.getUser("bob")).thenReturn(Some(bob))
    when(ud.getUser("nobody")).thenReturn(None)
    when(ud.updateUser(anyObject())).thenReturn(true)

    // denying nothing
    val req = Request(uri = Uri.uri("/groups/admin/deny/bob/nothing"))
    val reqwithsession = req.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res = app.dynamicWebRouter(reqwithsession)
    val resp = res.run
    resp.getSession.get.alerts.head.content must equal("bob did not apply to nothing, or has already been accepted/denied")

    // approving nothing
    val req2 = Request(uri = Uri.uri("/groups/admin/approve/bob/nothing"))
    val reqwithsession2 = req2.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res2 = app.dynamicWebRouter(reqwithsession2)
    val resp2 = res2.run
    resp2.getSession.get.alerts.head.content must equal("bob did not apply to nothing, or has already been accepted/denied")

    // denying non-existent user
    val req3 = Request(uri = Uri.uri("/groups/admin/deny/nobody/nothing"))
    val reqwithsession3 = req3.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res3 = app.dynamicWebRouter(reqwithsession3)
    val resp3 = res3.run
    resp3.getSession.get.alerts.head.content must equal("Can't find that user")

    // approving non-existent user
    val req4 = Request(uri = Uri.uri("/groups/admin/approve/nobody/nothing"))
    val reqwithsession4 = req4.copy(attributes = req.attributes.put(SessionManager.HYDRATEDSESSION, new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res4 = app.dynamicWebRouter(reqwithsession4)
    val resp4 = res4.run
    resp4.getSession.get.alerts.head.content must equal("Can't find that user")
  }
  "DynamicRouter's routes which require a session" should "redirect back to /" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val update = mock[Update]
    when(config.auth).thenReturn(authconfig)

    val app = new Webapp(config, pg, 9021, ud, crestapi = Some(crest), mapper = Some(db), updater = Some(update))

    app.dynamicWebRouter(Request(uri = Uri.uri("/signup/confirm"))).run.status must equal(Status.TemporaryRedirect)
    app.dynamicWebRouter(Request(uri = Uri.uri("/groups"))).run.status must equal(Status.TemporaryRedirect)
    app.dynamicWebRouter(Request(uri = Uri.uri("/groups/admin"))).run.status must equal(Status.TemporaryRedirect)
    app.dynamicWebRouter(Request(uri = Uri.uri("/ping"))).run.status must equal(Status.TemporaryRedirect)
    app.dynamicWebRouter(Request(uri = Uri.uri("/groups/admin/approve/foo/bar"))).run.status must equal(Status.TemporaryRedirect)
    app.dynamicWebRouter(Request(uri = Uri.uri("/groups/admin/deny/foo/bar"))).run.status must equal(Status.TemporaryRedirect)
    app.dynamicWebRouter(Request(uri = Uri.uri("/groups/apply/thing"))).run.status must equal(Status.TemporaryRedirect)
    app.dynamicWebRouter(Request(uri = Uri.uri("/groups/apply/thing"))).run.status must equal(Status.TemporaryRedirect)
    app.dynamicWebRouter(Request(method = Method.POST, uri = Uri.uri("/ping/global"))).run.status must equal(Status.SeeOther)
    app.dynamicWebRouter(Request(method = Method.POST, uri = Uri.uri("/ping/group"))).run.status must equal(Status.SeeOther)
  }
}
