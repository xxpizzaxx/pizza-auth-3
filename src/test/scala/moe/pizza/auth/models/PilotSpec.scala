package moe.pizza.auth.models

import moe.pizza.auth.models.Pilot.CrestToken
import moe.pizza.eveapi.ApiKey
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll

/**
  * Created by andi on 18/02/16.
  */
object PilotSpec extends Properties("Pilot") {

  property("getCrestKeys") = forAll { (charid: Long, token: String) =>
    val p = Pilot(null, null, null, null, null, null, null, null, List("%d:%s".format(charid,token)), null)
    p.getCrestTokens == List(new CrestToken(charid, token))
  }

  property("getApiKeys") = forAll { (id: Int, key: String) =>
    val p = Pilot(null, null, null, null, null, null, null, null, null, List("%d:%s".format(id, key)))
    p.getApiKeys == List(new ApiKey(id, key))
  }





}
