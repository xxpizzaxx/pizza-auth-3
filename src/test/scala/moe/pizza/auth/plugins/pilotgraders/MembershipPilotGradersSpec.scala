package moe.pizza.auth.plugins.pilotgraders

import moe.pizza.auth.models.Pilot
import moe.pizza.auth.plugins.pilotgraders.MembershipPilotGraders.{
  AlliancePilotGrader,
  CorporationPilotGrader,
  PublicAccessPilotGrader
}
import org.scalatest.{MustMatchers, WordSpec}

/**
  * Created by Andi on 25/02/2016.
  */
class MembershipPilotGradersSpec extends WordSpec with MustMatchers {

  "CorporationPilotGrader" when {
    "grading" should {
      "grade pilots who aren't in my corp as unclassified" in {
        val c = new CorporationPilotGrader("mycoolcorp")
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
        c.grade(p) must equal(Pilot.Status.unclassified)
      }
      "grade pilots who are in my corp as internal" in {
        val c = new CorporationPilotGrader("bobcorp")
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
        c.grade(p) must equal(Pilot.Status.internal)
      }
    }
  }

  "AlliancePilotGrader" when {
    "grading" should {
      "grade pilots who aren't in my alliance as unclassified" in {
        val c = new AlliancePilotGrader("mycoolalliance")
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
        c.grade(p) must equal(Pilot.Status.unclassified)
      }
      "grade pilots who are in my alliance as internal" in {
        val c = new AlliancePilotGrader("boballiance")
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
        c.grade(p) must equal(Pilot.Status.internal)
      }
    }
  }

  "PublicAccessPilotGrader" when {
    "grading" should {
      "grade pilots as ineligible" in {
        val c = new PublicAccessPilotGrader
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
        c.grade(p) must equal(Pilot.Status.ineligible)
      }
    }
  }

}
