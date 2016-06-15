package moe.pizza.auth.adapters

import moe.pizza.auth.bots.XmppBot
import moe.pizza.auth.interfaces.BroadcastService
import moe.pizza.auth.models.Pilot

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class XmppBroadcastService(host: String, password: String) extends BroadcastService {

  def getJabberServer(u: Pilot): String = u.accountStatus match {
    case Pilot.Status.internal => host
    case Pilot.Status.ally => s"allies.$host"
    case Pilot.Status.ineligible => s"public.$host"
    case _ => "none"
  }

  val xmppbot = new XmppBot
  xmppbot.connect("pingbot", host, password)

  override def sendAnnouncement(msg: String, from: String, to: List[Pilot]): Future[Int] = {
    Future {
      to.map { p =>
        xmppbot.sendMessage(s"${p.uid}@${getJabberServer(p)}", msg)
        1
      }.sum
    }
  }
}
