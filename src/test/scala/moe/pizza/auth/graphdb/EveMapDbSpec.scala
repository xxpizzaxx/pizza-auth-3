package moe.pizza.auth.graphdb

import org.scalatest.{MustMatchers, WordSpec}
import org.scalatest.mock.MockitoSugar

class EveMapDbSpec extends WordSpec with MustMatchers with MockitoSugar {

  "EveMapDb" when {
    "being used" should {
      "do all of the normal expected things" in {
        val e = new EveMapDb("map-tests1")
        // initialise the database
        e.withGraph{ g =>
          g.getEdgeType("gate") must not equal(null)
        }
        // fail gracefully on bad system names
        e.getDistanceBetweenSystemsByName("amor", "jota") must equal(None)

        // fail gracefully on bad system numbers
        e.getDistanceBetweenSystemsById(1, -42) must equal(None)

        // correctly find the distance between named systems
        e.getDistanceBetweenSystemsByName("Amarr", "Jita") must equal(Some(10))

        // correctly find the distance between system ids
        e.getDistanceBetweenSystemsById(30000142, 30004711) must equal(Some(40))

        // describe the distance between the same system and itself as 0
        e.getDistanceBetweenSystemsById(30000142, 30000142) must equal(Some(0))
        e.getDistanceBetweenSystemsByName("Amarr", "Amarr") must equal(Some(0))
        e.cleanUp()
      }
    }
  }

}
