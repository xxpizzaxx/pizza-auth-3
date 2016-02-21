package moe.pizza.auth.models

import com.fasterxml.jackson.databind.{ObjectMapper, JsonNode}
import moe.pizza.auth.models.Pilot.CrestToken
import moe.pizza.eveapi.ApiKey

object Pilot {
  val OM = new ObjectMapper()

  /**
    * Create a Pilot from a Map[String, List[String]], as returned by the simple Ldap Client
    *
    * @param m output from LDAP client
    * @return a constructed Pilot
    */
  def fromMap(m: Map[String, List[String]]): Pilot = {
    new Pilot(
      m.get("uid").flatMap(_.headOption).getOrElse("unknown"),
      m.get("accountstatus").flatMap(_.headOption).getOrElse("Expired"),
      m.get("alliance").flatMap(_.headOption).getOrElse("unknown"),
      m.get("corporation").flatMap(_.headOption).getOrElse("unknown"),
      m.get("charactername").flatMap(_.headOption).getOrElse("unknown"),
      m.get("email").flatMap(_.headOption).getOrElse("unknown"),
      m.get("metadata").flatMap(_.headOption).map(OM.readTree).getOrElse(OM.createObjectNode()),
      m.getOrElse("authGroups", List.empty[String]),
      m.getOrElse("crestTokens", List.empty[String]),
      m.getOrElse("apiKey", List.empty[String])
    )
  }

  case class CrestToken(characterID: Long, token: String)
}

case class Pilot(
                  uid: String,
                  accountStatus: String,
                  alliance: String,
                  corporation: String,
                  characterName: String,
                  email: String,
                  metadata: JsonNode,
                  authGroups: List[String],
                  crestTokens: List[String],
                  apiKeys: List[String]
                ) {
  def getCrestTokens: List[CrestToken] = {
    crestTokens.flatMap{t =>
      val r = t.split(":", -1)
      if (r.length == 2) {
        Some(CrestToken(r(0).toLong, r(1)))
      } else {
        None
      }
    }
  }
  def getApiKeys: List[ApiKey] = {
    apiKeys.flatMap{k =>
      val r = k.split(":", -1)
      if (r.length == 2) {
        Some(ApiKey(r(0).toInt, r(1)))
      } else {
        None
      }
    }

  }
}
