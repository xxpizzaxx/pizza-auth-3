package moe.pizza.auth.webapp

object Types {
  case class Alert(level: String, content: String)
  case class Session(accessToken: String, refreshToken: String, characterName: String, characterID: Long, alerts: List[Alert])
}
