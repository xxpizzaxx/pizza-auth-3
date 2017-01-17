package moe.pizza.auth.webapp.oauth

import java.util.UUID

import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.Json
import io.circe.generic.JsonCodec
import moe.pizza.auth.interfaces.UserDatabase
import moe.pizza.auth.webapp.Types.{HydratedSession, Session, Session2, SignupData}
import org.http4s.twirl._
import org.http4s.{Challenge, HttpService, Uri, UrlForm, _}
import org.http4s.dsl.{Root, _}
import org.http4s.circe._
import org.slf4j.LoggerFactory

case class OAuthApplication(name: String,
                            client_id: String,
                            secret: String,
                            callback: String)

case class OAuthAccessToken(access_token: String,
                            token_type: String,
                            expires_in: Int)

case class VerifyResponse(uid: String,
                          authGroups: List[String])

case class OAuthError(error: String, description: String)

class OAuthResource(portnumber: Int = 9021,
                    ud: UserDatabase,
                    applications: List[OAuthApplication]) {


  val log = LoggerFactory.getLogger(getClass)

  import moe.pizza.auth.webapp.Utils._

  // TODO: Refactor, straight up copied out of Webapp
  implicit class RichHydratedSession(hs: HydratedSession) {
    def toNormalSession = new Session(hs.alerts)

    def updatePilot: HydratedSession = {
      hs.pilot match {
        case Some(p) =>
          hs.copy(pilot = ud.getUser(p.uid))
        case None =>
          hs
      }
    }
  }

  def resource = HttpService {

    case req @ GET -> Root / "oauth" / "authorize" => {
      val clientID = req.params("client_id")
      val responseType = req.params("response_type")
      val callback = req.params("redirect_uri")
      val state = req.params("state")

      // TODO: check if clientID belongs to a registered application
      // if not:
      if(false) {
        BadRequest(OAuthError("unauthorized_client","Invalid ClientID").asJson)
      }

      // TODO: check if the callback is correct
      // if not:
      if(false) {
        BadRequest(OAuthError("invalid_request",
          "The callback URI does not match the value stored for this client").asJson)
      }

      // TODO: check if responseType is "code"
      // if not:
      if(false) {
        val uri = Uri.fromString(callback).toOption.get
          .withQueryParam("error", "unsupported_response_type")
          .withQueryParam("state", state)
        // redirect to callback
        Found(uri)
      }

      // Session stuff?
      req.getSession.map(_.updatePilot).flatMap(_.pilot) match {
        case Some(p) =>
          // Ask user to authorize application
          Ok(
            templates.html.base(
              "pizza-auth-3",
              templates.html.authorize("testapp",clientID,"callback","state"),
              req.getSession.map(_.toNormalSession),
              req.getSession.flatMap(_.pilot)
            )).attachSessionifDefined(
            req.getSession.map(_.copy(alerts = List())))
        case None =>
          // TODO: redirection back to here after login
          TemporaryRedirect(Uri.uri("/login"))
      }
    }

    case req @ POST -> Root / "oauth" / "authorize" => {
      // Session stuff?
      req.getSession.map(_.updatePilot).flatMap(_.pilot) match {
        case Some(p) =>
          req.decode[UrlForm] { data =>
            val clientID = data.getFirstOrElse("clientID", "")
            val callback = data.getFirstOrElse("callback", "")
            val state = data.getFirstOrElse("state", "")

            // TODO: check if clientID belongs to a registered application
            // (if someone changed it in the form)

            // TODO: check if the callback is correct
            // (if someone changed it in the form)

            // generate authentication code
            val code = UUID.randomUUID().toString()
            // TODO: save authentication code


            // build callback url
            val uri = Uri.fromString(callback).toOption.get
              .withQueryParam("state", state)
              .withQueryParam("code", code)

            // redirect to callback
            Found(uri)
          }
        case None =>
          // TODO: How can that happen?
          // What do?
          InternalServerError()
      }
    }
    case req @ GET -> Root / "oauth" / "token" => {
      val clientID = req.params("client_id")
      val clientSecret = req.params("client_secret")
      val grantType = req.params("grant_type")
      val code = req.params("code")

      // TODO: check if all parameters are there
      // if not:
      if(false) {
        BadRequest(OAuthError("invalid_request","Something is missing").asJson)
      }

      // TODO: check if clientID belongs to a registered application
      // if not:
      if(false) {
        BadRequest(OAuthError("invalid_client","Invalid ClientID").asJson)
      }

      // TODO: check if grantType is authorization_code
      // if not:
      if(false) {
        BadRequest(OAuthError("unsupported_grant_type",
          "Unsupported Grant Type").asJson)
      }


      // TODO: check if code is valid
      // if not:
      if(false) {
        BadRequest(OAuthError("invalid_grant",
          "Unsupported Grant Type").asJson)
      }

      // generate new access token
      val token = OAuthAccessToken(
        UUID.randomUUID().toString,
        "bearer",
        30*24*3600)

      // TODO: save access token

      // return access token as json
      Ok(token.asJson)
    }
    case req @ GET -> Root / "oauth" / "verify" => {
      val accessToken = req.headers.get(headers.Authorization)
        .map(_.credentials.value.stripPrefix("Bearer "))

      // TODO: check token
      // if not
      if(false) {
        Unauthorized(
          Challenge(scheme = "Bearer", realm = "Please use a valid access token"))
      }

      Ok(VerifyResponse("bob",List("ping")).asJson)
    }
  }
}
