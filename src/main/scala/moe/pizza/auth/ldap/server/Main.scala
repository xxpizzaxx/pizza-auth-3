package moe.pizza.auth.ldap.server

import moe.pizza.auth.ldap.client.LdapClient

/**
  * Created by Andi on 15/02/2016.
  */
object Main extends App {

  val LDAP_PORT = 389

  val e = new EmbeddedLdapServer("server", "ou=pizza", "localhost", LDAP_PORT)
  e.setPassword("testpassword")
  e.start()


  val client = new LdapClient("localhost", LDAP_PORT, "uid=admin,ou=system", "testpassword")
  import client._
  client.withConnection { c =>
    c.filter("", "(objectclass=*)").foreach {
      e => ()
        //println(e.toMap)
    }

  }


}
