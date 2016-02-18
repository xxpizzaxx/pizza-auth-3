package moe.pizza.auth.ldap.server

/**
  * Created by Andi on 15/02/2016.
  */
object Main extends App {

  val e = new EmbeddedLdapServer("server", "ou=pizza", "localhost", 389)
  e.setPassword("testpassword")
  e.start()


  val client = new LdapClient("localhost", 389, "uid=admin,ou=system", "testpassword")
  import client._
  client.withConnection { c =>
    c.filter("", "(objectclass=*)").foreach {
      e =>
        println(e.toMap)
    }

  }


}
