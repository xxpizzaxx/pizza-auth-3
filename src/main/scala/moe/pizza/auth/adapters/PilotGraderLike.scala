package moe.pizza.auth.adapters

import com.fasterxml.jackson.databind.JsonNode
import moe.pizza.auth.config.ConfigFile.ConfigFile
import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.plugins.pilotgraders.MembershipPilotGraders.{AlliancePilotGrader, CorporationPilotGrader}
import moe.pizza.auth.plugins.pilotgraders.{AlliedPilotGrader, CrestKeyGrader, InternalWhitelistPilotGrader}
import moe.pizza.crestapi.CrestApi
import moe.pizza.eveapi.{ApiKey, XmlApiKey}
import org.http4s.client.Client
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/**
  * Created by Andi on 28/02/2016.
  */
object PilotGraderLike {

  val logger = LoggerFactory.getLogger(getClass)

  trait PilotGraderLike[T] {
    def apply(j: JsonNode, c: ConfigFile)(implicit client: Client): T
  }

  object PilotGraderLike {

    implicit object AlliedPilotGrader
        extends PilotGraderLike[AlliedPilotGrader] {
      override def apply(j: JsonNode, c: ConfigFile)(
          implicit client: Client): AlliedPilotGrader = {
        val apikey =
          new ApiKey(j.get("keyId").asInt(), j.get("vCode").asText())
        logger.info(
          "registering AlliedPilotGrader with configuration %s".format(
            j.toString))
        new AlliedPilotGrader(
          Option(j.get("threshold")).map(_.asDouble).getOrElse(4.9),
          Option(j.get("usecorp")).map(_.asBoolean()).getOrElse(true),
          Option(j.get("usealliance")).map(_.asBoolean()).getOrElse(true),
          None,
          apikey
        )
      }
    }

    implicit object CrestKeyGrader extends PilotGraderLike[CrestKeyGrader] {
      override def apply(j: JsonNode, c: ConfigFile)(
          implicit client: Client): CrestKeyGrader = {
        logger.info(
          "registering CrestKeyGrader with configuration %s".format(
            j.toString))
        new CrestKeyGrader(
          new CrestApi(
            baseurl = Option(j.get("login_url"))
              .map(_.asText)
              .getOrElse(c.crest.loginUrl),
            cresturl = Option(j.get("crest_url"))
              .map(_.asText)
              .getOrElse(c.crest.crestUrl),
            Option(j.get("clientID"))
              .map(_.asText())
              .getOrElse(c.crest.clientID),
            Option(j.get("secretKey"))
              .map(_.asText())
              .getOrElse(c.crest.secretKey),
            Option(j.get("redirectUrl"))
              .map(_.asText())
              .getOrElse(c.crest.secretKey)
          )
        )
      }
    }

    implicit object CorporationPilotGrader
        extends PilotGraderLike[CorporationPilotGrader] {
      override def apply(j: JsonNode, c: ConfigFile)(
          implicit client: Client): CorporationPilotGrader = {
        logger.info(
          "registering CorporationPilotGrader with configuration %s".format(
            j.toString))
        new CorporationPilotGrader(
          Option(j.get("corporation"))
            .map(_.asText())
            .getOrElse(c.auth.corporation)
        )
      }
    }

    implicit object AlliancePilotGrader
        extends PilotGraderLike[AlliancePilotGrader] {
      override def apply(j: JsonNode, c: ConfigFile)(
          implicit client: Client): AlliancePilotGrader = {
        logger.info(
          "registering AlliancePilotGrader with configuration %s".format(
            j.toString))
        new AlliancePilotGrader(
          Option(j.get("alliance")).map(_.asText()).getOrElse(c.auth.alliance)
        )
      }
    }

    implicit object InternalWhitelistPilotGrader
        extends PilotGraderLike[InternalWhitelistPilotGrader] {
      override def apply(j: JsonNode, c: ConfigFile)(
          implicit client: Client): InternalWhitelistPilotGrader = {
        logger.info(
          "registering InternalWhitelistPilotGrader with configuration %s"
            .format(j.toString))
        new InternalWhitelistPilotGrader(
          new CrestApi(
            Option(j.get("baseurl")).map(_.asText).getOrElse(c.crest.crestUrl),
            Option(j.get("loginurl"))
              .map(_.asText)
              .getOrElse(c.crest.loginUrl),
            Option(j.get("clientID"))
              .map(_.asText())
              .getOrElse(c.crest.clientID),
            Option(j.get("secretKey"))
              .map(_.asText())
              .getOrElse(c.crest.secretKey),
            Option(j.get("redirectUrl"))
              .map(_.asText())
              .getOrElse(c.crest.secretKey)
          ),
          j.get("ids")
            .iterator()
            .asScala
            .toList
            .filter(_.isLong)
            .map(_.asLong())
        )
      }
    }

  }

  object PilotGraderFactory {
    def create[T](j: JsonNode, config: ConfigFile)(
        implicit pg: PilotGraderLike[T],
        client: Client): T = pg(j, config)

    def fromYaml(c: JsonNode, config: ConfigFile)(
        implicit client: Client): Option[PilotGrader] = {
      Option(c.get("type")).map(_.asText()) match {
        case Some(t) =>
          t match {
            case "AlliedPilotGrader" =>
              Some(create[AlliedPilotGrader](c, config))
            case "CrestKeyGrader" => Some(create[CrestKeyGrader](c, config))
            case "CorporationPilotGrader" =>
              Some(create[CorporationPilotGrader](c, config))
            case "AlliancePilotGrader" =>
              Some(create[AlliancePilotGrader](c, config))
            case _ => None
          }
        case None => None
      }
    }
  }

}
