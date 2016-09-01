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
    val p = Pilot(null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  List("%d:%s".format(charid, token)),
                  null)
    p.getCrestTokens == List(new CrestToken(charid, token))
  }

  property("getApiKeys") = forAll { (id: Int, key: String) =>
    val p = Pilot(null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  List("%d:%s".format(id, key)))
    p.getApiKeys == List(new ApiKey(id, key))
  }

  property("toJson") = forAll {
    (uid: String, status: String, alliance: String, corp: String,
     character: String, email: String, meta: String, groups: List[String]) =>
      val p =
        Pilot(uid,
              Pilot.Status.lookup.getOrElse(status, Pilot.Status.ineligible),
              alliance,
              corp,
              character,
              email,
              Pilot.OM.readTree("{\"meta\": \"%s\"}".format(meta)),
              groups,
              List.empty,
              List.empty)
      val json = p.toJson
      val p2 = Pilot.fromJson(json)
      p2.contains(p)
  }

  property("getCrestKeys badinput") = forAll { (s: String) =>
    val p =
      Pilot(null, null, null, null, null, null, null, null, List(s), null)
    s.contains(":") == !p.getCrestTokens.isEmpty
  }

  property("getApiKeys, badinput") = forAll { (s: String) =>
    val p =
      Pilot(null, null, null, null, null, null, null, null, null, List(s))
    s.contains(":") == !p.getApiKeys.isEmpty
  }

  property("getGroups") = forAll { (g: String, g2: String) =>
    val p = Pilot(null, null, null, null, null, null, null, null, null, null)
    val p2 = p.copy(authGroups = List(g, g2 + "-pending"))
    p2.getGroups == List(g) && p2.getPendingGroups == List(g2)
  }

}
