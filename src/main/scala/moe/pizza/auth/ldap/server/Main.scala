package moe.pizza.auth.ldap.server

/**
  * Created by Andi on 15/02/2016.
  */
object Main extends App {

  val e = new EmbeddedLdapServer("server", "o=pizza", "localhost", 389)
  e.start()

}
