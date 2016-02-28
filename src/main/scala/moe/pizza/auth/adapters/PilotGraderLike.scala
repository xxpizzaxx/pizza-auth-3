package moe.pizza.auth.adapters

import com.fasterxml.jackson.databind.JsonNode
import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.plugins.pilotgraders.MembershipPilotGraders.{AlliancePilotGrader, CorporationPilotGrader}
import moe.pizza.auth.plugins.pilotgraders.{CrestKeyGrader, AlliedPilotGrader}
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi.ApiKey

import scala.concurrent.ExecutionContext

/**
  * Created by Andi on 28/02/2016.
  */
object PilotGraderLike {

  trait PilotGraderLike[T] {
    def apply(j: JsonNode)(implicit ec: ExecutionContext): T
  }

  object PilotGraderLike {

    implicit object AlliedPilotGrader extends PilotGraderLike[AlliedPilotGrader] {
      override def apply(j: JsonNode)(implicit ec: ExecutionContext): AlliedPilotGrader = {
        implicit val apikey = new ApiKey(j.get("keyId").asInt(), j.get("vCode").asText())
        new AlliedPilotGrader(
          Option(j.get("threshold")).map(_.asDouble).getOrElse(4.9),
          Option(j.get("usecorp")).map(_.asBoolean()).getOrElse(true),
          Option(j.get("usealliance")).map(_.asBoolean()).getOrElse(true)
        )
      }
    }

    implicit object CrestKeyGrader extends PilotGraderLike[CrestKeyGrader] {
      override def apply(j: JsonNode)(implicit ec: ExecutionContext): CrestKeyGrader = new CrestKeyGrader(
        new CrestApi(
          Option(j.get("baseurl")).map(_.asText).getOrElse("https://login.eveonline.com/"),
          Option(j.get("loginurl")).map(_.asText).getOrElse("https://crest-tq.eveonline.com/"),
          j.get("clientID").asText(),
          j.get("secretKey").asText(),
          j.get("redirectUrl").asText()
        )
      )
    }

    implicit object CorporationPilotGrader extends PilotGraderLike[CorporationPilotGrader] {
      override def apply(j: JsonNode)(implicit ec: ExecutionContext): CorporationPilotGrader = {
        new CorporationPilotGrader(j.get("corporation").asText())
      }
    }

    implicit object AlliancePilotGrader extends PilotGraderLike[AlliancePilotGrader] {
      override def apply(j: JsonNode)(implicit ec: ExecutionContext): AlliancePilotGrader = {
        new AlliancePilotGrader(j.get("alliance").asText())
      }
    }

  }

  object PilotGraderFactory {
    def create[T](j: JsonNode)(implicit pg: PilotGraderLike[T], ec: ExecutionContext): T = pg(j)
    def fromYaml(c: JsonNode)(implicit ec: ExecutionContext): Option[PilotGrader] = {
      Option(c.get("type")).map(_.asText()) match {
        case Some(t) => t match {
          case "AlliedPilotGrader" => Some(create[AlliedPilotGrader](c))
          case "CrestKeyGrader" => Some(create[CrestKeyGrader](c))
          case "CorporationPilotGrader" => Some(create[CorporationPilotGrader](c))
          case "AlliancePilotGrader" => Some(create[AlliancePilotGrader](c))
          case _ => None
        }
        case None => None
      }
    }
  }
}
