package moe.pizza.auth.interfaces

import moe.pizza.auth.models.Pilot

import scala.concurrent.Future

/**
  * Created by Andi on 22/02/2016.
  */
trait BroadcastService {
  def sendAnnouncement(msg: String, from: String, to: List[Pilot]): Future[Int]
}
