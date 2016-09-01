package moe.pizza.auth.webapp

import moe.pizza.auth.config.ConfigFile.{
  AuthConfig,
  AuthGroupConfig,
  ConfigFile
}
import moe.pizza.auth.graphdb.EveMapDb
import moe.pizza.auth.interfaces.{BroadcastService, PilotGrader, UserDatabase}
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.tasks.Update
import moe.pizza.auth.webapp.Types._
import moe.pizza.auth.webapp.Utils._
import moe.pizza.crestapi.CrestApi
import moe.pizza.crestapi.CrestApi.{CallbackResponse, VerifyResponse}
import moe.pizza.eveapi.{EVEAPI, XMLApiResponse}
import moe.pizza.eveapi.generated.eve
import org.http4s.dsl._
import org.http4s.headers.Location
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Headers, Request, Status, Uri, _}
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AutocompleteSpec extends FlatSpec with MockitoSugar with MustMatchers {

  "DynamicRouter's AutoCompleter" should "autocomplete groups" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val groupconfig = mock[AuthGroupConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    when(config.auth).thenReturn(authconfig)
    when(authconfig.groups).thenReturn(groupconfig)

    when(groupconfig.closed).thenReturn(List("admin"))
    when(groupconfig.open).thenReturn(List("dota"))
    when(ud.getAllUsers()).thenReturn(Seq.empty[Pilot])

    val app = new Webapp(config,
                         pg,
                         9021,
                         ud,
                         crestapi = Some(crest),
                         mapper = Some(db))

    val res =
      app.dynamicWebRouter(Request(uri = Uri.uri("/ping/complete?term=dot")))

    val resp = res.run
    resp.status must equal(Ok)
    val bodytxt = EntityDecoder.decodeString(resp)(Charset.`UTF-8`).run
    bodytxt must equal("[\"dota\"]")
  }

  "DynamicRouter's AutoCompleter" should "autocomplete corps and alliances and order by length" in {
    val config = mock[ConfigFile]
    val authconfig = mock[AuthConfig]
    val groupconfig = mock[AuthGroupConfig]
    val ud = mock[UserDatabase]
    val pg = mock[PilotGrader]
    val crest = mock[CrestApi]
    val db = mock[EveMapDb]
    when(config.auth).thenReturn(authconfig)
    when(authconfig.groups).thenReturn(groupconfig)

    val blankPilot =
      Pilot(null, null, null, null, null, null, null, null, null, null)

    when(groupconfig.closed).thenReturn(List("admin"))
    when(groupconfig.open).thenReturn(List("dota"))
    when(ud.getAllUsers()).thenReturn(
      Seq(
        blankPilot.copy(corporation = "Love Squad",
                        alliance = "Black Legion."),
        blankPilot.copy(corporation = "Blackwater USA Inc.",
                        alliance = "Pandemic Legion")
      ))

    val app = new Webapp(config,
                         pg,
                         9021,
                         ud,
                         crestapi = Some(crest),
                         mapper = Some(db))

    val res =
      app.dynamicWebRouter(Request(uri = Uri.uri("/ping/complete?term=black")))

    val resp = res.run
    resp.status must equal(Ok)
    val bodytxt = EntityDecoder.decodeString(resp)(Charset.`UTF-8`).run
    bodytxt must equal("[\"Black Legion.\",\"Blackwater USA Inc.\"]")
  }
}
