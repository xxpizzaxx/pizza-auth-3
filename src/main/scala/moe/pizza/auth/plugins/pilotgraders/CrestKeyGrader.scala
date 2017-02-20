package moe.pizza.auth.plugins.pilotgraders

import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.models.Pilot.Status
import moe.pizza.crestapi.CrestApi
import org.http4s.client.Client

import scala.util.Try

/**
  * Created by Andi on 28/02/2016.
  */
class CrestKeyGrader(c: CrestApi)(implicit val client: Client)
    extends PilotGrader {
  override def grade(p: Pilot): Status.Value = {
    p.getCrestTokens.flatMap { t =>
      Try {
        c.refresh(t.token).unsafePerformSync
      }.toOption
    }.map { f =>
      val verify = c.verify(f.access_token).unsafePerformSync
      verify.CharacterName
    }.find { _ == p.characterName } match {
      case Some(k) =>
        Status.unclassified
      case None =>
        Status.expired
    }

  }
}
