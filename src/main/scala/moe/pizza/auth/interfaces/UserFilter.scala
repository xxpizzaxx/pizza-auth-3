package moe.pizza.auth.interfaces

import moe.pizza.auth.models.Pilot

/**
  * Created by Andi on 23/02/2016.
  */
trait UserFilter {
  def filter(users: Seq[Pilot], rule: String): Seq[Pilot]
}
