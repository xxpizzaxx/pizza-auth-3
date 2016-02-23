package moe.pizza.auth.models

import com.fasterxml.jackson.databind.{ObjectMapper, JsonNode}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import moe.pizza.auth.models.Pilot.CrestToken
import moe.pizza.eveapi.ApiKey

import scala.util.Try

object Pilot {
  val OM = new ObjectMapper()
  OM.registerModule(DefaultScalaModule)

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

  def fromJson(s: String): Option[Pilot] = {
    Try {
      OM.readValue(s, classOf[Pilot])
    }.toOption
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
      val i = t.indexOf(":")
      if (i > -1) {
        Some(CrestToken(t.substring(0, i).toLong, t.substring(i+1, t.length)))
      } else {
        None
      }
    }
  }
  def getApiKeys: List[ApiKey] = {
    apiKeys.flatMap{k =>
      val i = k.indexOf(":")
      if (i > -1) {
        Some(ApiKey(k.substring(0, i).toInt, k.substring(i+1, k.length)))
      } else {
        None
      }
    }

  }

  def toJson: String = {
    Pilot.OM.writeValueAsString(this)
  }

  def toJsonNode: JsonNode = {
    Pilot.OM.readTree(this.toJson)
  }

}
