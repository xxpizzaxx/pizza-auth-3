package moe.pizza.auth.adapters

import moe.pizza.auth.bots.XmppBot
import moe.pizza.auth.interfaces.BroadcastService
import moe.pizza.auth.models.Pilot

import scala.concurrent.Future

class XmppBroadcastService(host: String, password: String) extends BroadcastService {

  val xmppbot = new XmppBot
  xmppbot.connect("pingbot", host, password)

  override def sendAnnouncement(msg: String, from: String, to: List[Pilot]): Future[Int] = {
    Future {
      to.map { p =>
        xmppbot.sendMessage(s"${p.uid}@$host", msg)
        1
      }.sum
    }
  }
}
