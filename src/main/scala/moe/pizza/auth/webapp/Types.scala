package moe.pizza.auth.webapp

import moe.pizza.auth.models.Pilot
import moe.pizza.crestapi.CrestApi.VerifyResponse

object Types {
  case class Alert(level: String, content: String)
  // TODO: redirect maybe Option[Uri] ? jackson / circe problem
  case class Session(alerts: List[Alert], redirect: Option[String])
  case class Session2(alerts: List[Alert],
                      redirect: Option[String],
                      uid: Option[String],
                      signupData: Option[SignupData])
  case class SignupData(verify: VerifyResponse, refresh: String)
  case class HydratedSession(alerts: List[Alert],
                             redirect: Option[String],
                             pilot: Option[Pilot],
                             signupData: Option[SignupData])
}
