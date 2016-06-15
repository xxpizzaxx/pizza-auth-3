package moe.pizza.auth.plugins.pilotgraders

import kafka.utils.Logging
import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.models.Pilot.Status
import moe.pizza.auth.plugins.pilotgraders.AlliedPilotGrader.SavedContactList
import moe.pizza.eveapi.generated.corp
import moe.pizza.eveapi.{XmlApiKey, ApiKey, EVEAPI}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.{Try, Failure, Success}


object AlliedPilotGrader {
  case class SavedContactList(cachedUntil: DateTime, pilots: Seq[String], corporations: Seq[String], alliances: Seq[String])

  val CORPORATION = 2
  val ALLIANCE = 16159
  val CHARACTERS = Range(1373, 1386)

  def transformContacts(cachedUntil: DateTime, contacts: Seq[corp.ContactList.Row]) = {
    val grouped = contacts.groupBy(_.contactTypeID)
    SavedContactList(
      cachedUntil,
      grouped.filterKeys(i => CHARACTERS.contains(i.toInt)).values.flatten.map(_.contactName).toSeq,
      grouped.getOrElse(CORPORATION, Seq.empty).map(_.contactName),
      grouped.getOrElse(ALLIANCE, Seq.empty).map(_.contactName)
    )
  }
}


class AlliedPilotGrader(threshold: Double, usecorp: Boolean, usealliance: Boolean, val eve: Option[EVEAPI] = None, apikey: XmlApiKey)(implicit val ec: ExecutionContext) extends PilotGrader {

  val logger = LoggerFactory.getLogger(getClass)
  val eveapi = eve.getOrElse(new EVEAPI(key = Some(apikey)))
  val allies = pullAllies()

  allies match {
    case Some(a) =>
      logger.info(s"loaded AlliedPilotGrader with these entities loaded:")
      logger.info(s"characters: ${a.pilots}")
      logger.info(s"corps: ${a.corporations}")
      logger.info(s"alliances: ${a.alliances}")
    case None =>
      logger.info(s"loaded AlliedPilotGrader, but failed to load a configuration from CCP")
  }

  def pullAllies(): Option[SavedContactList] = {
    val res = Try{eveapi.corp.ContactList().sync()}
    res match {
      case Success(r) =>
        val corp = r.result.filter(_.name == "corporateContactList").flatMap(_.row).filter(_ => usecorp).filter(_.standing > threshold)
        val alli = r.result.filter(_.name == "allianceContactList").flatMap(_.row).filter(_ => usealliance).filter(_.standing > threshold)
        val contacts = corp ++ alli
        logger.info(s"successfully refreshed contact list")
        Some(AlliedPilotGrader.transformContacts(r.cachedUntil, contacts))
      case Failure(f) =>
        logger.info("failed to refresh contact list")
        None
    }
  }

  override def grade(p: Pilot): Status.Value = {
    logger.info(s"running AlliedPilotGrader against ${p.characterName}/${p.corporation}/${p.alliance}")
    logger.info(s"it's cached list is ${allies}")
    allies match {
      case Some(a) =>
        /*
        if (a.cachedUntil.plusHours(1).isBeforeNow) {
          logger.info(s"refreshing contact list, it expired, it was cached until ${a.cachedUntil}")
          val newallies = pullAllies()
          if (newallies.isDefined) {
            allies = newallies
            grade(p)
          } else {
            logger.info("failed to refresh, classifying with old data")
            p match {
              case _ if a.pilots.exists(_ == p.characterName) => Status.ally
              case _ if a.corporations.exists(_ == p.corporation) => Status.ally
              case _ if a.alliances.exists(_ == p.alliance) => Status.ally
              case _ => Status.unclassified
            }
          }
        } else {
        */
          // we've got a valid contact list
          p match {
            case _ if a.pilots.exists(_ == p.characterName) => Status.ally
            case _ if a.corporations.exists(_ == p.corporation) => Status.ally
            case _ if a.alliances.exists(_ == p.alliance) => Status.ally
            case _ => Status.unclassified
          }
        /*
        }
        */
      case None =>
        logger.warn("no contact list loaded")
        Pilot.Status.unclassified
    }

  }
}
