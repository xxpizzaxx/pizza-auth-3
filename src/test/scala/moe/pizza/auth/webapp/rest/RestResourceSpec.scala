package moe.pizza.auth.webapp.rest

import moe.pizza.auth.config.ConfigFile.{AuthConfig, ConfigFile}
import moe.pizza.auth.graphdb.EveMapDb
import moe.pizza.auth.interfaces.{BroadcastService, PilotGrader, UserDatabase}
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.tasks.Update
import moe.pizza.crestapi.CrestApi
import org.http4s.headers.Location
import org.http4s._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}
import scala.concurrent.Future
import org.mockito.Matchers._
import scala.concurrent.ExecutionContext.Implicits.global

class RestResourceSpec extends FlatSpec with MockitoSugar with MustMatchers {

  "RestResource" should "send pings" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val broadcaster = mock[BroadcastService]
    when(config.auth).thenReturn(authconfig)
    when(broadcaster.sendAnnouncement(anyString(), anyString(), anyObject()))
      .thenReturn(Future { 0 })

    val resource = new RestResource(config,
                                    pg,
                                    9021,
                                    ud,
                                    crestapi = Some(crest),
                                    mapper = Some(db),
                                    broadcasters = List(broadcaster))

    val res = resource.resource(
      Request(uri = Uri.uri("/api/v1/ping/group/groupname"))
        .withBody(
          "{\"message\": \"I like turtles\", \"from\": \"restbot\", \"to\": \"groupname\"}")
        .run)

    val resp = res.run
    resp.status.code must equal(200)
    val bodytxt =
      res.flatMap(EntityDecoder.decodeString(_)(Charset.`UTF-8`)).run
    bodytxt must equal("{\"total\":0}")
  }

  "RestResource" should "complain about bad json" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val broadcaster = mock[BroadcastService]
    when(config.auth).thenReturn(authconfig)
    when(broadcaster.sendAnnouncement(anyString(), anyString(), anyObject()))
      .thenReturn(Future { 0 })

    val resource = new RestResource(config,
                                    pg,
                                    9021,
                                    ud,
                                    crestapi = Some(crest),
                                    mapper = Some(db),
                                    broadcasters = List(broadcaster))

    val res = resource.resource(Request(
      uri = Uri.uri("/api/v1/ping/group/groupname")).withBody("{\"me").run)

    val resp = res.run
    resp.status.code must equal(400)
    val bodytxt =
      res.flatMap(EntityDecoder.decodeString(_)(Charset.`UTF-8`)).run
    bodytxt must equal("Invalid JSON")
  }

  "RestResource" should "complain about missing keys in the json" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    val broadcaster = mock[BroadcastService]
    when(config.auth).thenReturn(authconfig)
    when(broadcaster.sendAnnouncement(anyString(), anyString(), anyObject()))
      .thenReturn(Future { 0 })

    val resource = new RestResource(config,
                                    pg,
                                    9021,
                                    ud,
                                    crestapi = Some(crest),
                                    mapper = Some(db),
                                    broadcasters = List(broadcaster))

    val res = resource.resource(
      Request(uri = Uri.uri("/api/v1/ping/group/groupname"))
        .withBody("{\"message\": \"I like turtles\"}")
        .run)

    val resp = res.run
    resp.status.code must equal(400)
    val bodytxt =
      res.flatMap(EntityDecoder.decodeString(_)(Charset.`UTF-8`)).run
    bodytxt must equal(
      "{\"type\":\"bad_post_body\",\"message\":\"Unable to process your post body, please format it correctly\"}")
  }
}
