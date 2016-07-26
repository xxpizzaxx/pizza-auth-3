package moe.pizza.auth.interfaces

import moe.pizza.auth.models.Pilot

import scala.concurrent.Future

object BroadcastService {
  implicit class BroadcastableUserDatabase(ud: UserDatabase) {
    def sendGroupAnnouncement(broadcasters: List[BroadcastService], message: String, from: String, users: List[Pilot]): List[Future[Int]] = {
      broadcasters.map { b =>
        b.sendAnnouncement(message, from, users)
      }
    }
  }
}


trait BroadcastService {
  def sendAnnouncement(msg: String, from: String, to: List[Pilot]): Future[Int]
}
