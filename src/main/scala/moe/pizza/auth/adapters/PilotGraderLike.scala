package moe.pizza.auth.adapters

import com.fasterxml.jackson.databind.JsonNode
import moe.pizza.auth.config.ConfigFile.ConfigFile
import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.plugins.pilotgraders.MembershipPilotGraders.{AlliancePilotGrader, CorporationPilotGrader}
import moe.pizza.auth.plugins.pilotgraders.{CrestKeyGrader, AlliedPilotGrader}
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi.ApiKey
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

/**
  * Created by Andi on 28/02/2016.
  */
object PilotGraderLike {

  val logger = LoggerFactory.getLogger(getClass)

  trait PilotGraderLike[T] {
    def apply(j: JsonNode, c: ConfigFile)(implicit ec: ExecutionContext): T
  }

  object PilotGraderLike {

    implicit object AlliedPilotGrader extends PilotGraderLike[AlliedPilotGrader] {
      override def apply(j: JsonNode, c: ConfigFile)(implicit ec: ExecutionContext): AlliedPilotGrader = {
        implicit val apikey = new ApiKey(j.get("keyId").asInt(), j.get("vCode").asText())
        logger.info("registering AlliedPilotGrader with configuration %s".format(j.toString))
        new AlliedPilotGrader(
          Option(j.get("threshold")).map(_.asDouble).getOrElse(4.9),
          Option(j.get("usecorp")).map(_.asBoolean()).getOrElse(true),
          Option(j.get("usealliance")).map(_.asBoolean()).getOrElse(true)
        )
      }
    }

    implicit object CrestKeyGrader extends PilotGraderLike[CrestKeyGrader] {
      override def apply(j: JsonNode, c: ConfigFile)(implicit ec: ExecutionContext): CrestKeyGrader = {
        logger.info("registering CrestKeyGrader with configuration %s".format(j.toString))
        new CrestKeyGrader(
        new CrestApi(
          Option(j.get("baseurl")).map(_.asText).getOrElse(c.crest.crestUrl),
          Option(j.get("loginurl")).map(_.asText).getOrElse(c.crest.loginUrl),
          Option(j.get("clientID")).map(_.asText()).getOrElse(c.crest.clientID),
          Option(j.get("secretKey")).map(_.asText()).getOrElse(c.crest.secretKey),
          Option(j.get("redirectUrl")).map(_.asText()).getOrElse(c.crest.secretKey)
        )
      )
      }
    }

    implicit object CorporationPilotGrader extends PilotGraderLike[CorporationPilotGrader] {
      override def apply(j: JsonNode, c: ConfigFile)(implicit ec: ExecutionContext): CorporationPilotGrader = {
        logger.info("registering CorporationPilotGrader with configuration %s".format(j.toString))
        new CorporationPilotGrader(
          Option(j.get("corporation")).map(_.asText()).getOrElse(c.auth.corporation)
        )
      }
    }

    implicit object AlliancePilotGrader extends PilotGraderLike[AlliancePilotGrader] {
      override def apply(j: JsonNode, c: ConfigFile)(implicit ec: ExecutionContext): AlliancePilotGrader = {
        logger.info("registering AlliancePilotGrader with configuration %s".format(j.toString))
        new AlliancePilotGrader(
          Option(j.get("alliance")).map(_.asText()).getOrElse(c.auth.alliance)
        )
      }
    }

  }

  object PilotGraderFactory {
    def create[T](j: JsonNode, config: ConfigFile)(implicit pg: PilotGraderLike[T], ec: ExecutionContext): T = pg(j, config)
    def fromYaml(c: JsonNode, config: ConfigFile)(implicit ec: ExecutionContext): Option[PilotGrader] = {
      Option(c.get("type")).map(_.asText()) match {
        case Some(t) => t match {
          case "AlliedPilotGrader" => Some(create[AlliedPilotGrader](c, config))
          case "CrestKeyGrader" => Some(create[CrestKeyGrader](c, config))
          case "CorporationPilotGrader" => Some(create[CorporationPilotGrader](c, config))
          case "AlliancePilotGrader" => Some(create[AlliancePilotGrader](c, config))
          case _ => None
        }
        case None => None
      }
    }
  }
}
