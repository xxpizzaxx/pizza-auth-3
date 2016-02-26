package moe.pizza.auth.plugins

import kafka.utils.Logging
import moe.pizza.auth.interfaces.PilotGrader
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.models.Pilot.Status
import moe.pizza.auth.plugins.AlliedPilotGrader.SavedContactList
import moe.pizza.eveapi.generated.corp
import moe.pizza.eveapi.{ApiKey, EVEAPI}
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import scalaxb._


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


class AlliedPilotGrader(threshold: Double, usecorp: Boolean, usealliance: Boolean, val eve: Option[EVEAPI] = None)(implicit apikey: ApiKey, implicit val ec: ExecutionContext) extends PilotGrader with Logging {
  val eveapi = eve.getOrElse(new EVEAPI())
  var allies = pullAllies()



  def pullAllies(): Option[SavedContactList] = {
    val res = eveapi.corp.ContactList().sync()
    res match {
      case Success(r) =>
        val corp = r.result.filter(_.name == "corporateContactList").flatMap(_.row).filter(_ => usecorp).filter(_.standing > threshold)
        val alli = r.result.filter(_.name == "allianceContactList").flatMap(_.row).filter(_ => usealliance).filter(_.standing > threshold)
        val contacts = corp ++ alli
        logger.info("successfully refreshed contact list")
        Some(AlliedPilotGrader.transformContacts(r.cachedUntil, contacts))
      case Failure(f) =>
        logger.info("failed to refresh contact list")
        None
    }
  }

  override def grade(p: Pilot): Status.Value = {
    allies match {
      case Some(a) =>
        if (a.cachedUntil.isBeforeNow) {
          logger.info("refreshing contact list, it expired")
          val newallies = pullAllies()
          if (newallies.isDefined) {
            allies = newallies
            grade(p)
          } else {
            logger.info("failed to refresh, classifying with old data")
            p match {
              case _ if a.pilots contains p.characterName => Status.ally
              case _ if a.corporations contains p.corporation => Status.ally
              case _ if a.alliances contains p.alliance => Status.ally
              case _ => Status.unclassified
            }
          }
        } else {
          // we've got a valid contact list
          p match {
            case _ if a.pilots contains p.characterName => Status.ally
            case _ if a.corporations contains p.corporation => Status.ally
            case _ if a.alliances contains p.alliance => Status.ally
            case _ => Status.unclassified
          }
        }
      case None =>
        logger.warn("no contact list loaded")
        Pilot.Status.unclassified
    }

  }
}
