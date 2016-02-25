package moe.pizza.auth.models

import com.fasterxml.jackson.core.{JsonParser, JsonGenerator, Version}
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import moe.pizza.auth.models.Pilot.CrestToken
import moe.pizza.eveapi.ApiKey

import scala.util.Try

object Pilot {
  val OM = new ObjectMapper()
  OM.registerModule(DefaultScalaModule)
  val sm = new SimpleModule("pizza-auth-serializer", new Version(1, 0, 0, null, "moe.pizza", "pizza-auth-3"))
  sm.addSerializer(new EnumSerializer)
  sm.addDeserializer(classOf[Status.Value], new EnumDeserializer)
  OM.registerModule(sm)

  // ser/der for the Pilot Status enum
  class EnumDeserializer extends JsonDeserializer[Status.Value] {
    override def deserialize(p: JsonParser, ctxt: DeserializationContext): Status.Value = {
      Pilot.Status.lookup.getOrElse(p.getValueAsString, Status.expired)
    }
    override def handledType() = classOf[Status.Value]
  }
  class EnumSerializer extends JsonSerializer[Status.Value] {
    override def serialize(value: Status.Value, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
      gen.writeString(value.toString)
    }
    override def handledType() = classOf[Status.Value]
  }

  object Status extends Enumeration {
    type Status = Value
    val internal = Value("Internal")
    val ally = Value("Ally")
    val expired = Value("Expired")
    val banned = Value("Banned")
    val ineligible = Value("Ineligible")
    val unclassified = Value("Unclassified")
    val lookup = Map("Internal" -> internal, "Ally" -> ally, "Expired" -> expired, "Banned" -> banned, "Ineligible" -> ineligible, "Unclassified" -> unclassified)
  }

  /**
    * Create a Pilot from a Map[String, List[String]], as returned by the simple Ldap Client
    *
    * @param m output from LDAP client
    * @return a constructed Pilot
    */
  def fromMap(m: Map[String, List[String]]): Pilot = {
    new Pilot(
      m.get("uid").flatMap(_.headOption).getOrElse("unknown"),
      Pilot.Status.lookup.getOrElse(m.get("accountstatus").flatMap(_.headOption).getOrElse("Expired"), Status.expired),
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
                  accountStatus: Pilot.Status.Status,
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
