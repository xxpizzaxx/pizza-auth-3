package moe.pizza.auth.models

import com.fasterxml.jackson.databind.{ObjectMapper, JsonNode}

object Pilot {
  val OM = new ObjectMapper()

  /**
    * Create a Pilot from a Map[String, List[String]], as returned by the simple Ldap Client
    * @param m output from LDAP client
    * @return a constructed Pilot
    */
  def fromMap(m: Map[String, List[String]]): Pilot = {
    new Pilot(
      m.get("uid").flatMap(_.headOption).getOrElse("unknown"),
      m.get("accountStatus").flatMap(_.headOption).getOrElse("Expired"),
      m.get("alliance").flatMap(_.headOption).getOrElse("unknown"),
      m.get("corporation").flatMap(_.headOption).getOrElse("unknown"),
      m.get("characterName").flatMap(_.headOption).getOrElse("unknown"),
      m.get("email").flatMap(_.headOption).getOrElse("unknown"),
      m.get("metadata").flatMap(_.headOption).map(OM.readTree).getOrElse(OM.createObjectNode()),
      m.getOrElse("authGroups", List.empty[String]),
      m.getOrElse("crestTokens", List.empty[String]),
      m.getOrElse("apiKey", List.empty[String])
    )
  }
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
                  apiKey: List[String]
                )
