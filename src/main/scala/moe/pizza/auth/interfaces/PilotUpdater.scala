package moe.pizza.auth.interfaces

import moe.pizza.auth.models.Pilot


trait PilotUpdater {
  def run(p: Pilot): Pilot
}
