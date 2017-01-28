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

import scala.collection.concurrent.TrieMap


case class OAuthApplication(name: String,
                            clientId: String,
                            secret: String,
                            callback: String)

case class OAuthAccessToken(access_token: String,
                            token_type: String,
                            expires_in: Int)

// TODO: Expiry
case class OAuthStoredAuthenticationCode(code: String,
                                         clientId: String,
                                         uid: String)

// TODO: Expiry
case class OAuthStoredToken(access_token: String,
                            token_type: String,
                            clientId: String,
                            uid: String)

case class VerifyResponse(uid: String,
                          authGroups: List[String])

case class OAuthError(error: String, description: String)

class OAuthResource(portnumber: Int = 9021,
                    ud: UserDatabase,
                    applications: List[OAuthApplication]) {

  // TODO: Most likely NOT the way to do it
  val applicationMap = applications match {
    case null =>
      Map[String, OAuthApplication]()
    case a =>
      a.map(app => app.clientId -> app) toMap
  }

  val authenticationCodes = new TrieMap[String, OAuthStoredAuthenticationCode]
  val tokens = new TrieMap[String, OAuthStoredToken]


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
      (req.params("client_id"), req.params("response_type"),
        req.params("redirect_uri"), req.params("state")) match {
        // wrong client ID
        case (clientId, _,_,_) if !(applicationMap isDefinedAt clientId) =>
          BadRequest(OAuthError("unauthorized_client","Invalid ClientID").asJson)

        // Wrong callback
        case (clientId, _, callbackUri, _) if applicationMap.get(clientId).get.callback != callbackUri =>
          BadRequest(OAuthError("invalid_request",
          "The callback URI does not match the value stored for this client").asJson)

        // Wrong responseType
        case (_,responseType,callback,state) if responseType != "code" =>
          val uri = Uri.fromString(callback).toOption.get
            .withQueryParam("error", "unsupported_response_type")
            .withQueryParam("state", state)
          // redirect to callback
          Found(uri)

        // Request looks ok
        case (clientId, responseType, redirectUri, state) =>
          val app = applicationMap.get(clientId).get
          // Session stuff?
          req.getSession.map(_.updatePilot).flatMap(_.pilot) match {
            case Some(p) =>
              // Ask user to authorize application
              Ok(
                templates.html.base(
                  "pizza-auth-3",
                  templates.html.authorize(app.name,clientId,redirectUri,state),
                  req.getSession.map(_.toNormalSession),
                  req.getSession.flatMap(_.pilot)
                )).attachSessionifDefined(
                req.getSession.map(_.copy(alerts = List())))
            case None =>
              // TODO: redirection back to here after login
              TemporaryRedirect(Uri.uri("/login"))
          }
      }
    }

    case req @ POST -> Root / "oauth" / "authorize" => {
      // Session stuff?
      req.getSession.map(_.updatePilot).flatMap(_.pilot) match {
        case Some(p) =>
          req.decode[UrlForm] { data =>
            val clientId = data.getFirstOrElse("clientId", "")
            val callback = data.getFirstOrElse("callback", "")
            val state = data.getFirstOrElse("state", "")

            (clientId, callback, state) match {
              case (clientId, _, _) if !(applicationMap isDefinedAt clientId) =>
                BadRequest("Invalid clientId")
              case (clientId, callback, _) if applicationMap.get(clientId).get.callback != callback =>
                BadRequest("Invalid callback")
              case (clientId, callback, state) =>
                // generate authentication code
                val code = UUID.randomUUID().toString()
                // save authentication code
                val stored = OAuthStoredAuthenticationCode(code, clientId, p.uid)
                authenticationCodes += code -> stored
                // build callback url
                val uri = Uri.fromString(callback).toOption.get
                  .withQueryParam("state", state)
                  .withQueryParam("code", code)
                // redirect to callback
                Found(uri)
            }
          }
        case None =>
          // TODO: How can that happen?
          // What do?
          InternalServerError()
      }
    }
    case req @ GET -> Root / "oauth" / "token" => {
      val clientId = req.params("client_id")
      val clientSecret = req.params("client_secret")
      val grantType = req.params("grant_type")
      val code = req.params("code")

      // TODO: check if all parameters are there
      // if not:
      if(false) {
        BadRequest(OAuthError("invalid_request","Something is missing").asJson)
      }

      (clientId, clientSecret, grantType, code) match {
        // check if clientID belongs to a registered application
        case (clientId, _, _, _) if !(applicationMap isDefinedAt clientId) =>
          BadRequest(OAuthError("invalid_client", "Invalid ClientID").asJson)

        // check if grantType is authorization_code
        case (_, _, grantType, _) if grantType != "authorization_code" =>
          BadRequest(OAuthError("unsupported_grant_type",
            "Unsupported Grant Type").asJson)

        // check if code is valid
        case (clientId, clientSecret, grantType, code)
          if applicationMap.get(clientId).get.secret == clientSecret =>
          authenticationCodes.get(code) match {
            case Some(code) =>
              code match {
                case c if code.clientId == clientId =>
                  // generate new access token
                  val storeToken = OAuthStoredToken(
                    UUID.randomUUID().toString,
                    "Bearer", clientId, c.uid)

                  val token = OAuthAccessToken(
                    storeToken.access_token,
                    storeToken.token_type, 30 * 24 * 3600)

                  // save access token
                  tokens += storeToken.access_token -> storeToken

                  // delete authentication code
                  authenticationCodes -= c.code

                  // return access token as json
                  Ok(token.asJson)
                case _ =>
                  BadRequest(OAuthError("invalid_grant",
                    "Invalid Token").asJson)
              }
            case None =>
              BadRequest(OAuthError("invalid_grant",
                "Invalid Token").asJson)
          }
        case (_, _, _, _) =>
          BadRequest(OAuthError("invalid_client",
            "Invalid Client").asJson)
      }
    }
    case req @ GET -> Root / "oauth" / "verify" => {
      req.headers.get(headers.Authorization)
        .map(_.credentials.value.stripPrefix("Bearer ")) match {
        case Some(accessToken) =>
          // check token
          tokens.get(accessToken) match {
            case Some(storedToken) =>
              ud.getUser(storedToken.uid) match {
                case Some(p) =>
                  Ok(VerifyResponse(p.uid,p.authGroups).asJson)
                case None =>
                  BadRequest("User associated with token not found")
              }
            case None =>
              Unauthorized(
                Challenge(scheme = "Bearer", realm = "Please use a valid access token"))
          }
        case None =>
          BadRequest("Please supply token")
      }
    }
  }
}
