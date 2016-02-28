package moe.pizza.auth.adapters

import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.models.Pilot.Status
import org.scalatest.{FlatSpec, MustMatchers}
import org.scalatest.mock.MockitoSugar

/**
  * Created by Andi on 28/02/2016.
  */
class GraderChainSpec extends FlatSpec with MustMatchers with MockitoSugar {

  "GraderChain" should "chain graders and return the first result one gives" in {
    val grader1 = new PilotGrader {
        override def grade(p: Pilot): Status.Value = Status.banned
      }
    val grader2 = new PilotGrader {
      override def grade(p: Pilot): Status.Value = Status.ineligible
    }
    val chain = List(grader1, grader2)
    val graderchain = new GraderChain(chain)
    val p = Pilot("bob", Pilot.Status.internal, "myalliance", "mycorp", "Bob", "bob@bob.com", Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")), List("group1", "group3"), List("123:bobkey"), List.empty )
    graderchain.grade(p) must equal(Status.banned)
  }

  "GraderChain" should "fall through all unclassified results" in {
    val grader1 = new PilotGrader {
        override def grade(p: Pilot): Status.Value = Status.unclassified
      }
    val grader2 = new PilotGrader {
      override def grade(p: Pilot): Status.Value = Status.internal
    }
    val chain = List(grader1, grader2)
    val graderchain = new GraderChain(chain)
    val p = Pilot("bob", Pilot.Status.internal, "myalliance", "mycorp", "Bob", "bob@bob.com", Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")), List("group1", "group3"), List("123:bobkey"), List.empty )
    graderchain.grade(p) must equal(Status.internal)
  }

}
