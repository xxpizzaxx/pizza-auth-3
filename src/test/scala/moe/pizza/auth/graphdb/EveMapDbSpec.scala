package moe.pizza.auth.graphdb

import org.scalatest.{MustMatchers, WordSpec}
import org.scalatest.mock.MockitoSugar

class EveMapDbSpec extends WordSpec with MustMatchers with MockitoSugar {

  "EveMapDb" when {
    "being instantiated" should {
      "populate the db if required" in {
        val e = new EveMapDb("map-tests1")
        e.withGraph{ g =>
          g.getEdgeType("gate") must not equal(null)
        }
        e.cleanUp()
      }
    }
    "being used to find distances" should {
      "fail gracefully on bad system names" in {
        val e = new EveMapDb("map-tests2")
        e.getDistanceBetweenSystemsByName("amor", "jota") must equal(None)
        e.cleanUp()
      }
      "fail gracefully on bad system numbers" in {
        val e = new EveMapDb("map-tests2")
        e.getDistanceBetweenSystemsById(1, -42) must equal(None)
        e.cleanUp()
      }
      "correctly find the distance between named systems" in {
        val e = new EveMapDb("map-tests2")
        e.getDistanceBetweenSystemsByName("Amarr", "Jita") must equal(Some(10))
        e.cleanUp()
      }
      "correctly find the distance between system ids" in {
        val e = new EveMapDb("map-tests2")
        e.getDistanceBetweenSystemsById(30000142, 30004711) must equal(Some(40))
        e.cleanUp()
      }
      "describe the distance between the same system and itself as 0" in {
        val e = new EveMapDb("map-tests2")
        e.getDistanceBetweenSystemsById(30000142, 30000142) must equal(Some(0))
        e.getDistanceBetweenSystemsByName("Amarr", "Amarr") must equal(Some(0))
        e.cleanUp()
      }
    }
  }

}
