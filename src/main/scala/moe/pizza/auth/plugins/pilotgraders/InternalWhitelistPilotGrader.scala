package moe.pizza.auth.plugins.pilotgraders

import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.models.Pilot.Status
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi._

import scala.concurrent.ExecutionContext
import scala.util.Try


class InternalWhitelistPilotGrader(c: CrestApi, ids: List[Long])(implicit val ec: ExecutionContext) extends PilotGrader {
  override def grade(p: Pilot): Status.Value = {
    p.getCrestTokens.flatMap { t =>
      Try {
        c.refresh(t.token).sync()
      }.toOption
    }.map{ f =>
      val verify = c.verify(f.access_token).sync()
      verify.characterID
    }.find{ids.contains} match {
      case Some(k) =>
        Status.internal
      case None =>
        Status.unclassified
    }

  }
}
