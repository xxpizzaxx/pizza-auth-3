package moe.pizza.auth.adapters

import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.models.Pilot.Status

/**
  * Created by Andi on 28/02/2016.
  */
class GraderChain(graders: Seq[PilotGrader]) extends PilotGrader {
  override def grade(p: Pilot): Status.Value = {
    graders.foldLeft(Status.unclassified) { (status, nextGrader) =>
      status match {
        case Status.unclassified => nextGrader.grade(p)
        case s => s
      }
    }
  }
}
