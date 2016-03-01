package moe.pizza.auth.webapp

object Types {
  case class Alert(level: String, content: String)
  case class Session(characterName: String, alerts: List[Alert])
}
