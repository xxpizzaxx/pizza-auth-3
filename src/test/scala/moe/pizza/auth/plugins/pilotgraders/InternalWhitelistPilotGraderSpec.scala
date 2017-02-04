package moe.pizza.auth.plugins.pilotgraders

import moe.pizza.auth.models.Pilot
import moe.pizza.auth.plugins.pilotgraders.MembershipPilotGraders.{AlliancePilotGrader, CorporationPilotGrader, PublicAccessPilotGrader}
import moe.pizza.crestapi.CrestApi
import moe.pizza.crestapi.CrestApi.{CallbackResponse, VerifyResponse}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{MustMatchers, WordSpec}
import org.mockito.Mockito._
import org.http4s.client.blaze.PooledHttp1Client

import scalaz.concurrent.Task

class InternalWhitelistPilotGraderSpec
    extends WordSpec
    with MustMatchers
    with MockitoSugar {

  implicit val client = PooledHttp1Client() //TODO: no mocking?

  "InternalWhitelistPilotGrader" when {
    "grading" should {
      "grade pilots who are in the list as internal" in {
        val crest = mock[CrestApi]
        when(crest.refresh("REF")).thenReturn(Task {
          new CallbackResponse("access", "type", 1000, Some("refresh"))
        })
        when(crest.verify("access")).thenReturn(Task {
          new VerifyResponse(1, "Bob", "", "", "", "", "")
        })
        val iwpg = new InternalWhitelistPilotGrader(crest, List(1L, 2L, 3L))
        val p = new Pilot("bob",
                          Pilot.Status.unclassified,
                          "boballiance",
                          "bobcorp",
                          "Bob",
                          "none@none",
                          Pilot.OM.createObjectNode(),
                          List.empty[String],
                          List("1:REF"),
                          List.empty[String])
        iwpg.grade(p) must equal(Pilot.Status.internal)
        verify(crest).refresh("REF")
        verify(crest).verify("access")
      }
      "grade pilots who are not in the list as unclassified" in {
        val crest = mock[CrestApi]
        when(crest.refresh("REF")).thenReturn(Task {
          new CallbackResponse("access", "type", 1000, Some("refresh"))
        })
        when(crest.verify("access")).thenReturn(Task {
          new VerifyResponse(1, "Bob", "", "", "", "", "")
        })
        val iwpg = new InternalWhitelistPilotGrader(crest, List(2L, 3L))
        val p = new Pilot("bob",
                          Pilot.Status.unclassified,
                          "boballiance",
                          "bobcorp",
                          "Bob",
                          "none@none",
                          Pilot.OM.createObjectNode(),
                          List.empty[String],
                          List("1:REF"),
                          List.empty[String])
        iwpg.grade(p) must equal(Pilot.Status.unclassified)
        verify(crest).refresh("REF")
        verify(crest).verify("access")
      }
    }
  }

}
