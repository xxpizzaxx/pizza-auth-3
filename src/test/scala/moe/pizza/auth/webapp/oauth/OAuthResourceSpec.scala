package moe.pizza.auth.webapp.oauth

import moe.pizza.auth.interfaces.UserDatabase
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.webapp.SessionManager
import moe.pizza.auth.webapp.Types.{Alert, HydratedSession}
import org.http4s.{Headers, _}
import org.http4s.util.CaseInsensitiveString
import org.http4s.circe._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.http4s.headers.Authorization
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OAuthResourceSpec extends FlatSpec with MockitoSugar with MustMatchers {

  "OAuthResource - authorize" should "only support authorization code" in {
    val clientId = "123"
    val callback = "http://localhost"
    val state = "state"
    val applications = List(OAuthApplication("testapp",clientId,"234",callback))
    val ud = mock[UserDatabase]

    val resource = new OAuthResource(9021, ud, applications)

    val req = Request(uri = Uri
      .uri("/oauth/authorize")
      .withQueryParam("response_type","token")
      .withQueryParam("client_id", clientId)
      .withQueryParam("redirect_uri", callback)
      .withQueryParam("state",state))

    val res = resource.resource.run(req)

    val resp = res.run
    resp.status must equal(Status.Found)
    val location = resp.headers.get(CaseInsensitiveString("location")).get.value
    assert(location startsWith callback)
    val params = Uri.fromString(location).toOption.get.params
    params("state") must equal(state)
    params("error") must equal("unsupported_response_type")
  }

  "OAuthResource - authorize" should "check callback URL" in {
    val clientID = "123"
    val callback = "http://localhost"
    val state = "state"
    val applications = List(OAuthApplication("testapp",clientID,"234",callback))
    val ud = mock[UserDatabase]

    val resource = new OAuthResource(9021, ud, applications)

    val req = Request(uri = Uri
      .uri("/oauth/authorize")
      .withQueryParam("response_type","code")
      .withQueryParam("client_id", clientID)
      .withQueryParam("redirect_uri", "http://totallyNotLocalhost")
      .withQueryParam("state",state))

    val res = resource.resource.run(req)

    val resp = res.run
    resp.status must equal(Status.BadRequest)
    val error = resp.as[OAuthError](jsonOf[OAuthError]).run
    error.error must equal("invalid_request")
  }

  "OAuthResource - authorize" should "check clientID" in {
    val clientID = "123"
    val callback = "http://localhost"
    val state = "state"
    val applications = List(OAuthApplication("testapp",clientID,"234",callback))
    val ud = mock[UserDatabase]

    val resource = new OAuthResource(9021, ud, applications)

    val req = Request(uri = Uri
      .uri("/oauth/authorize")
      .withQueryParam("response_type","code")
      .withQueryParam("client_id", "totallyWrongID")
      .withQueryParam("redirect_uri", "http://totallyNotLocalhost")
      .withQueryParam("state",state))

    val res = resource.resource.run(req)

    val resp = res.run
    resp.status must equal(Status.BadRequest)
    val error = resp.as[OAuthError](jsonOf[OAuthError]).run
    error.error must equal("unauthorized_client")
  }

  "OAuthResource - authorize" should "ask a logged in user for authorization" in {
    val appName = "testapp"
    val clientID = "123"
    val callback = "http://localtest/callback"
    val state = "test"
    val applications = List(OAuthApplication(appName,clientID,"234",callback))
    val ud = mock[UserDatabase]

    val resource = new OAuthResource(9021, ud, applications)

    val bob = new Pilot("bob",
      null,
      null,
      null,
      null,
      null,
      null,
      List(),
      null,
      null)

    when(ud.getUser("bob")).thenReturn(Some(bob))

    val req = Request(uri = Uri
        .uri("/oauth/authorize")
          .withQueryParam("response_type","code")
          .withQueryParam("client_id", clientID)
          .withQueryParam("redirect_uri",callback)
          .withQueryParam("state",state))

    val reqwithsession = req.copy(
      attributes = req.attributes.put(
        SessionManager.HYDRATEDSESSION,
        new HydratedSession(List.empty[Alert], Some(bob), None)))
    val res = resource.resource.run(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.Ok)
    val bodytxt = EntityDecoder.decodeString(resp)(Charset.`UTF-8`).run
    assert(bodytxt contains appName)
    assert(bodytxt contains "Authorize")
  }
  "OAuthResource - authorize" should "ask for login first if user isnt logged in" in {
    val appName = "testapp"
    val clientID = "123"
    val callback = "http://localtest/callback"
    val state = "test"
    val applications = List(OAuthApplication(appName,clientID,"234",callback))
    val ud = mock[UserDatabase]

    val resource = new OAuthResource(9021, ud, applications)

    val bob = new Pilot("bob",
      null,
      null,
      null,
      null,
      null,
      null,
      List(),
      null,
      null)

    when(ud.getUser("bob")).thenReturn(Some(bob))

    val req = Request(uri = Uri
      .uri("/oauth/authorize")
      .withQueryParam("response_type","code")
      .withQueryParam("client_id", clientID)
      .withQueryParam("redirect_uri",callback)
      .withQueryParam("state",state))

    val reqwithsession = req.copy(
      attributes = req.attributes.put(
        SessionManager.HYDRATEDSESSION,
        new HydratedSession(List.empty[Alert], None, None))) // not logged in
    val res = resource.resource.run(reqwithsession)

    val resp = res.run
    resp.status must equal(Status.TemporaryRedirect)
    val location = resp.headers.get(CaseInsensitiveString("location")).get.value
    location must equal("/login")
  }
  "OAuthResource" should "redirect to callback url and return usable access token" in {
    val appName = "testapp"
    val clientId = "123"
    val clientSecret = "234"
    val callback = "http://localtest/callback"
    val state = "teststate"

    val applications = List(OAuthApplication(appName,clientId,clientSecret,callback))
    val ud = mock[UserDatabase]

    val resource = new OAuthResource(9021, ud, applications)

    val bob = new Pilot("bob",
      null,
      null,
      null,
      null,
      null,
      null,
      List("ping"),
      null,
      null)

    when(ud.getUser("bob")).thenReturn(Some(bob))

    val req = Request(
      method = Method.POST,
      uri = Uri.uri("/oauth/authorize"),
      body = UrlForm.entityEncoder
        .toEntity(UrlForm(
          "clientId" -> clientId,
          "callback" -> callback,
          "state" -> state)).run.body)

    val reqwithsession = req.copy(
      attributes = req.attributes.put(
        SessionManager.HYDRATEDSESSION,
        new HydratedSession(List.empty[Alert], Some(bob), None)))

    val res = resource.resource.run(reqwithsession)
    val resp = res.run
    resp.status must equal(Status.Found)
    val location = resp.headers.get(CaseInsensitiveString("location")).get.value
    assert(location startsWith callback)

    val params = Uri.fromString(location).toOption.get.params
    params("state") must equal(state)

    val code = params("code")

    val req2 = Request(uri = Uri
      .uri("/oauth/token")
      .withQueryParam("grant_type","authorization_code")
      .withQueryParam("client_id", clientId)
      .withQueryParam("client_secret", clientSecret)
      .withQueryParam("code",code))

    val res2 = resource.resource.run(req2)
    val resp2 = res2.run
    resp2.status.code must equal(200)
    val accessToken = resp2.as[OAuthAccessToken](jsonOf[OAuthAccessToken]).run

    accessToken.token_type must equal("Bearer")


    val req3 = Request(
      uri = Uri.uri("/oauth/verify"),
      headers = Headers(new Authorization(OAuth2BearerToken(accessToken.access_token))))

    val res3 = resource.resource.run(req3)
    val resp3 = res3.run
    resp3.status.code must equal(200)
    val verify = resp3.as[VerifyResponse](jsonOf[VerifyResponse]).run
    verify.uid must equal("bob")
    verify.authGroups must equal(List("ping"))
  }
  "OAuthResource - token" should "only support authorization code" in {
    val appName = "testapp"
    val clientID = "123"
    val clientSecret = "234"
    val callback = "http://localtest/callback"
    val state = "teststate"

    val applications = List(OAuthApplication(appName,clientID,clientSecret,callback))
    val ud = mock[UserDatabase]

    val resource = new OAuthResource(9021, ud, applications)

    val req = Request(uri = Uri
      .uri("/oauth/token")
      .withQueryParam("grant_type","different_grant")
      .withQueryParam("client_id", clientID)
      .withQueryParam("client_secret", clientSecret)
      .withQueryParam("code","totallyWrongCode"))

    val res = resource.resource.run(req)
    val resp = res.run
    resp.status.code must equal(400)
    val error = resp.as[OAuthError](jsonOf[OAuthError]).run

    error.error must equal("unsupported_grant_type")
  }
  "OAuthResource - token" should "verify clientID and secret" in {
    val appName = "testapp"
    val clientID = "123"
    val clientSecret = "234"
    val callback = "http://localtest/callback"
    val state = "teststate"

    val applications = List(OAuthApplication(appName,clientID,clientSecret,callback))
    val ud = mock[UserDatabase]

    val resource = new OAuthResource(9021, ud, applications)

    val req = Request(uri = Uri
      .uri("/oauth/token")
      .withQueryParam("grant_type","authorization_code")
      .withQueryParam("client_id", clientID)
      .withQueryParam("client_secret", "wrong secret")
      .withQueryParam("code","totallyWrongCode"))

    val res = resource.resource.run(req)
    val resp = res.run
    resp.status.code must equal(400)
    val error = resp.as[OAuthError](jsonOf[OAuthError]).run

    error.error must equal("invalid_client")
  }
  "OAuthResource - token" should "throw error for wrong authorization code" in {
    val appName = "testapp"
    val clientID = "123"
    val clientSecret = "234"
    val callback = "http://localtest/callback"
    val state = "teststate"

    val applications = List(OAuthApplication(appName,clientID,clientSecret,callback))
    val ud = mock[UserDatabase]

    val resource = new OAuthResource(9021, ud, applications)

    val req = Request(uri = Uri
      .uri("/oauth/token")
      .withQueryParam("grant_type","authorization_code")
      .withQueryParam("client_id", clientID)
      .withQueryParam("client_secret", clientSecret)
      .withQueryParam("code","totallyWrongCode"))

    val res = resource.resource.run(req)
    val resp = res.run
    resp.status.code must equal(400)
    val error = resp.as[OAuthError](jsonOf[OAuthError]).run

    error.error must equal("invalid_grant")
  }

  "OAuthResource - verify" should "throw error for wrong accessToken" in {
    val appName = "testapp"
    val clientID = "123"
    val clientSecret = "234"
    val callback = "http://localtest/callback"
    val state = "teststate"

    val applications = List(OAuthApplication(appName,clientID,clientSecret,callback))
    val ud = mock[UserDatabase]

    val resource = new OAuthResource(9021, ud, applications)

    val req = Request(
      uri = Uri.uri("/oauth/verify"),
      headers = Headers(new Authorization(OAuth2BearerToken("totallyNotAToken"))))

    val res = resource.resource.run(req)
    val resp = res.run
    resp.status must equal(Status.Unauthorized)
  }
}
