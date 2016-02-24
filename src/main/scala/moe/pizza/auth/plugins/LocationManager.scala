package moe.pizza.auth.plugins

import moe.pizza.auth.models.Pilot
import moe.pizza.crestapi.CrestApi

import scala.concurrent.ExecutionContext

/**
  * Created by Andi on 21/02/2016.
  */
object LocationManager {

  /*
  def locateUsers(crest: CrestApi)(pilots: List[Pilot])(implicit ec: ExecutionContext) = {

    pilots.map { p =>
      p.getCrestTokens.map { token =>
        (p, crest.character.location(token.characterID, token.token))
      }
    }
  }
  */

}
