package moe.pizza.auth.plugins

import moe.pizza.auth.models.Pilot
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi._

import scala.concurrent.ExecutionContext

/**
  * Created by Andi on 21/02/2016.
  */
object LocationManager {

  def locateUsers(crest: CrestApi)(pilots: List[Pilot])(implicit ec: ExecutionContext) = {

    pilots.map { p =>
      p.getCrestTokens.map { token =>
        val refreshed = crest.refresh(token.token).sync()
        (p, crest.character.location(token.characterID, refreshed.access_token))
      }
    }
  }

}
