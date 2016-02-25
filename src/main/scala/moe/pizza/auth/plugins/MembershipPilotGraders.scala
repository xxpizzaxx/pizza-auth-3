package moe.pizza.auth.plugins

import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.models.Pilot.Status

/**
  * Created by Andi on 25/02/2016.
  */
object MembershipPilotGraders {

  class CorporationPilotGrader(mycorp: String) extends PilotGrader {
    override def grade(p: Pilot): Status.Value = {
      p.corporation match {
        case s if s == mycorp => Pilot.Status.internal
        case _ => Pilot.Status.unclassified
      }
    }
  }

  class AlliancePilotGrader(myalliance: String) extends PilotGrader {
    override def grade(p: Pilot): Status.Value = {
      p.alliance match {
        case s if s == myalliance => Pilot.Status.internal
        case _ => Pilot.Status.unclassified
      }
    }
  }

  class PublicAccessPilotGrader extends PilotGrader {
    override def grade(p: Pilot): Status.Value = Pilot.Status.ineligible
  }

}
