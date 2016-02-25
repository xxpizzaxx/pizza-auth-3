package moe.pizza.auth.interfaces

import moe.pizza.auth.models.Pilot

/**
  * Created by Andi on 25/02/2016.
  */
trait PilotGrader {
  def grade(p: Pilot): Pilot.Status.Value
}
