package moe.pizza.auth.plugins

import moe.pizza.auth.models.Pilot
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi._

import org.http4s.client.Client

/**
  * Created by Andi on 21/02/2016.
  */
object LocationManager {

  def locateUsers(crest: CrestApi)(pilots: List[Pilot])(
      implicit client: Client) = {

    pilots.map { p =>
      p.getCrestTokens.map { token =>
        val refreshed = crest.refresh(token.token).unsafePerformSync
        val verify = crest.verify(refreshed.access_token).unsafePerformSync
        (p, verify.CharacterName,
         crest.character.location(token.characterID, refreshed.access_token))
      }
    }
  }

}
