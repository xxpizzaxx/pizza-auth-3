package moe.pizza.auth.plugins.pilotgraders

import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.models.Pilot.Status
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi.{EVEAPI, SyncableFuture}

import scala.concurrent.ExecutionContext
import scala.util.Try

class CaldariCrestKeyGrader(c: CrestApi)(implicit val ec: ExecutionContext) extends PilotGrader {
  val eveapi = new EVEAPI()

  override def grade(p: Pilot): Status.Value = {
    p.getCrestTokens.flatMap { t =>
      Try {c.refresh(t.token).sync()}.toOption
    }.map{ f =>
      val verify = c.verify(f.access_token).sync()
      verify.characterID
    }.exists{ id =>
      eveapi.char.CharacterInfo(id.toInt).sync().result.race == "Caldari"
    } match {
      case true =>
        // caldari is the enemy
        Status.banned
      case false =>
        // non-caldari is okay
        Status.unclassified
    }
  }
}
