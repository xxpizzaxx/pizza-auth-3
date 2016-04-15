package moe.pizza.auth.webapp

import moe.pizza.auth.models.Pilot

object Types {
  case class Alert(level: String, content: String)
  case class Session(alerts: List[Alert])
  case class Session2(alerts: List[Alert], pilot: Option[Pilot])
}
