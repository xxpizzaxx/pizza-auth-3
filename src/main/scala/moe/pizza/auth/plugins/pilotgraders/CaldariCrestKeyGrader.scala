package moe.pizza.auth.plugins.pilotgraders

import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.models.Pilot.Status
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi.{EVEAPI}
import org.http4s.client.Client

import scala.util.Try

class CaldariCrestKeyGrader(c: CrestApi, eve: Option[EVEAPI] = None)(
    implicit val client: Client)
    extends PilotGrader {
  val eveapi = eve.getOrElse(new EVEAPI(client))

  override def grade(p: Pilot): Status.Value = {
    p.getCrestTokens.flatMap { t =>
      Try { c.refresh(t.token).unsafePerformSync }.toOption
    }.map { f =>
      val verify = c.verify(f.access_token).unsafePerformSync
      verify.CharacterID
    }.exists { id =>
      eveapi.char.CharacterInfo(id.toInt).unsafePerformSync.result.race == "Caldari"
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
