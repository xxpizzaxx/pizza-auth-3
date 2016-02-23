package moe.pizza.auth.interfaces

import moe.pizza.auth.models.Pilot

/**
  * Created by Andi on 21/02/2016.
  */
trait UserDatabase {
  def addUser(p: Pilot): Boolean
  def deleteUser(p: Pilot): Boolean
  def updateUser(p: Pilot): Boolean
  def authenticateUser(uid: String, password: String): Option[Pilot]
  def getUser(uid: String): Option[Pilot]
  def setPassword(p: Pilot, password: String): Boolean
  def getUsers(filter: String): List[Pilot]
  def getAllUsers(): Seq[Pilot]
}
