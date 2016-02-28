package moe.pizza.auth.plugins

import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.models.Pilot.Status
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi.SyncableFuture

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
  * Created by Andi on 28/02/2016.
  */
class CrestKeyGrader(c: CrestApi)(implicit val ec: ExecutionContext) extends PilotGrader {
  override def grade(p: Pilot): Status.Value = {
    p.getCrestTokens.flatMap { t =>
      Try {
        c.refresh(t.token).sync()
      }.toOption
    }.map{ f =>
      val verify = c.verify(f.access_token).sync()
      verify.characterName
    }.find{_ == p.characterName} match {
      case Some(k) =>
        Status.unclassified
      case None =>
        Status.expired
    }

  }
}
