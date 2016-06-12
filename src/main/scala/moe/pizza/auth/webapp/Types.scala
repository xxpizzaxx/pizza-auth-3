package moe.pizza.auth.webapp

import moe.pizza.auth.models.Pilot
import moe.pizza.crestapi.CrestApi.VerifyResponse

object Types {
  case class Alert(level: String, content: String)
  case class Session(alerts: List[Alert])
  case class Session2(alerts: List[Alert], uid: Option[String], signupData: Option[SignupData])
  case class SignupData(verify: VerifyResponse, refresh: String)
  case class HydratedSession(alerts: List[Alert], pilot: Option[Pilot], signupData: Option[SignupData])
}
