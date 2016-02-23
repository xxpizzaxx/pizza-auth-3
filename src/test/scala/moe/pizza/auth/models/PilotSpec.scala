package moe.pizza.auth.models

import moe.pizza.auth.models.Pilot.CrestToken
import moe.pizza.eveapi.ApiKey
import org.scalacheck.{Gen, Properties}
import org.scalacheck.Prop.forAll
import org.scalacheck.Gen.choose

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


  property("toJson") = forAll {
    (uid: String, status: String, alliance: String, corp: String, character: String, email: String, meta: String, groups: List[String] ) =>
      val p = Pilot(uid, status, alliance, corp, character, email, Pilot.OM.readTree("{\"meta\": \"%s\"}".format(meta)), groups, List.empty, List.empty )
      val json = p.toJson
      val p2 = Pilot.fromJson(json)
      p2.contains(p)
  }




}
