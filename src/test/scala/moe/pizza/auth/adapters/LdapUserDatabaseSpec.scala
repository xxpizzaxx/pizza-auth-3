package moe.pizza.auth.adapters

import java.io.File
import java.util.UUID

import moe.pizza.auth.ldap.client.LdapClient
import moe.pizza.auth.ldap.client.LdapClient._
import moe.pizza.auth.ldap.server.EmbeddedLdapServer
import moe.pizza.auth.models.Pilot
import org.apache.directory.api.ldap.model.entry.DefaultEntry
import org.scalatest.{FlatSpec, MustMatchers}

/**
  * Created by andi on 18/02/16.
  */
class LdapUserDatabaseSpec extends FlatSpec with MustMatchers {

  def createTempFolder(suffix: String): File = {
    val base = new File(new File(System.getProperty("java.io.tmpdir")), "pizza-auth-test")
    base.delete()
    val dir = new File(base, UUID.randomUUID().toString + suffix)
    dir.mkdirs()
    dir
  }

  "inserting a user into LDAP" should "create the entry in a way that can be extracted again" in {
    val tempfolder = createTempFolder("loadusertest")
    try {
      val server = new EmbeddedLdapServer(tempfolder.toString, "ou=pizza", "localhost", 3390, instanceName = "pizza-auth-ldap-user-db-spec")
      server.setPassword("testpassword")
      server.start()
      // TODO find a way to make a schemamanager without the server
      val schema = server.directoryService.getSchemaManager
      // use the client
      val c = new LdapClient("localhost", 3390, "uid=admin,ou=system", "testpassword")
      // wrap it in an LUD
      val lud = new LdapUserDatabase(c, schema)
      val p = new Pilot("lucia_denniard", Pilot.Status.internal, "Confederation of xXPIZZAXx", "Love Squad", "Lucia Denniard", "lucia@pizza.moe", Pilot.OM.createObjectNode(), List(), List(), List())
      lud.addUser(p, "luciapassword") must equal(true)
      c.withConnection { con =>
        val r = con.filter("ou=pizza", "(uid=lucia_denniard)")
        val user = r.toList.headOption.map(_.toMap).map(Pilot.fromMap)
        user must equal(Some(p))
      }
      server.stop()
    } finally {
      tempfolder.delete()
    }
  }

  "inserting a user into LDAP then reading it back out" should "create the entry in a way that can be extracted again" in {
    val tempfolder = createTempFolder("loadusertest2")
    try {
      val server = new EmbeddedLdapServer(tempfolder.toString, "ou=pizza", "localhost", 3391, instanceName = "pizza-auth-ldap-user-db-spec-1")
      server.setPassword("testpassword")
      server.start()
      // TODO find a way to make a schemamanager without the server
      val schema = server.directoryService.getSchemaManager
      // use the client
      val c = new LdapClient("localhost", 3391, "uid=admin,ou=system", "testpassword")
      // wrap it in an LUD
      val lud = new LdapUserDatabase(c, schema)
      val p = new Pilot("lucia_denniard", Pilot.Status.internal, "Confederation of xXPIZZAXx", "Love Squad", "Lucia Denniard", "lucia@pizza.moe", Pilot.OM.createObjectNode(), List(), List(), List())
      lud.addUser(p, "luciapassword") must equal(true)
      lud.getUser("lucia_denniard") must equal(Some(p))
      server.stop()
    } finally {
      tempfolder.delete()
    }
  }

  "inserting a user into LDAP then authenticating with it" should "authenticate with the right password" in {
    val tempfolder = createTempFolder("loadusertest2")
    try {
      val server = new EmbeddedLdapServer(tempfolder.toString, "ou=pizza", "localhost", 3392, instanceName = "pizza-auth-ldap-user-db-spec-2")
      server.setPassword("testpassword")
      server.start()
      // TODO find a way to make a schemamanager without the server
      val schema = server.directoryService.getSchemaManager
      // use the client
      val c = new LdapClient("localhost", 3392, "uid=admin,ou=system", "testpassword")
      // wrap it in an LUD
      val lud = new LdapUserDatabase(c, schema)
      val p = new Pilot("lucia_denniard", Pilot.Status.internal, "Confederation of xXPIZZAXx", "Love Squad", "Lucia Denniard", "lucia@pizza.moe", Pilot.OM.createObjectNode(), List(), List(), List())
      lud.addUser(p, "luciapassword") must equal(true)
      lud.getUser("lucia_denniard") must equal(Some(p))
      lud.authenticateUser("lucia_denniard", "notluciapassword") must equal(None)
      lud.authenticateUser("lucia_denniard", "luciapassword") must equal(Some(p))
      server.stop()
    } finally {
      tempfolder.delete()
    }
  }

  "inserting a user into LDAP and changing it's password" should "authenticate with the right passwords" in {
    val tempfolder = createTempFolder("loadusertest2")
    try {
      val server = new EmbeddedLdapServer(tempfolder.toString, "ou=pizza", "localhost", 3393, instanceName = "pizza-auth-ldap-user-db-spec-3")
      server.setPassword("testpassword")
      server.start()
      // TODO find a way to make a schemamanager without the server
      val schema = server.directoryService.getSchemaManager
      // use the client
      val c = new LdapClient("localhost", 3393, "uid=admin,ou=system", "testpassword")
      // wrap it in an LUD
      val lud = new LdapUserDatabase(c, schema)
      val p = new Pilot("lucia_denniard", Pilot.Status.internal, "Confederation of xXPIZZAXx", "Love Squad", "Lucia Denniard", "lucia@pizza.moe", Pilot.OM.createObjectNode(), List(), List(), List())
      lud.addUser(p, "luciapassword") must equal(true)
      lud.getUser("lucia_denniard") must equal(Some(p))
      lud.authenticateUser("lucia_denniard", "notluciapassword") must equal(None)
      lud.authenticateUser("lucia_denniard", "luciapassword") must equal(Some(p))
      lud.setPassword(p, "luciaNEWpassword") must equal(true)
      lud.authenticateUser("lucia_denniard", "luciapassword") must equal(None)
      lud.authenticateUser("lucia_denniard", "luciaNEWpassword") must equal(Some(p))
      server.stop()
    } finally {
      tempfolder.delete()
    }
  }

  "deleting a user" should "delete the user" in {
    val tempfolder = createTempFolder("loadusertest2")
    try {
      val server = new EmbeddedLdapServer(tempfolder.toString, "ou=pizza", "localhost", 3394, instanceName = "pizza-auth-ldap-user-db-spec-4")
      server.setPassword("testpassword")
      server.start()
      // TODO find a way to make a schemamanager without the server
      val schema = server.directoryService.getSchemaManager
      // use the client
      val c = new LdapClient("localhost", 3394, "uid=admin,ou=system", "testpassword")
      // wrap it in an LUD
      val lud = new LdapUserDatabase(c, schema)
      val p = new Pilot("lucia_denniard", Pilot.Status.internal, "Confederation of xXPIZZAXx", "Love Squad", "Lucia Denniard", "lucia@pizza.moe", Pilot.OM.createObjectNode(), List(), List(), List())
      lud.addUser(p, "luciapassword") must equal(true)
      lud.getUser("lucia_denniard") must equal(Some(p))
      lud.authenticateUser("lucia_denniard", "notluciapassword") must equal(None)
      lud.authenticateUser("lucia_denniard", "luciapassword") must equal(Some(p))
      lud.setPassword(p, "luciaNEWpassword") must equal(true)
      lud.authenticateUser("lucia_denniard", "luciapassword") must equal(None)
      lud.authenticateUser("lucia_denniard", "luciaNEWpassword") must equal(Some(p))
      lud.getUsers("uid=lucia_denniard") must equal(List(p))
      lud.getAllUsers() must equal(Seq(p))
      lud.deleteUser(p) must equal(true)
      lud.getUser("lucia_denniard") must equal(None)
      lud.getAllUsers() must equal(Seq())
      lud.getUsers("uid=lucia_denniard") must equal(List())
      server.stop()
    } finally {
      tempfolder.delete()
    }
  }
  "updating users" should "update users" in {
    val tempfolder = createTempFolder("loadusertest2")
    try {
      val server = new EmbeddedLdapServer(tempfolder.toString, "ou=pizza", "localhost", 3395, instanceName = "pizza-auth-ldap-user-db-spec-5")
      server.setPassword("testpassword")
      server.start()
      // TODO find a way to make a schemamanager without the server
      val schema = server.directoryService.getSchemaManager
      // use the client
      val c = new LdapClient("localhost", 3395, "uid=admin,ou=system", "testpassword")
      // wrap it in an LUD
      val lud = new LdapUserDatabase(c, schema)
      val p = new Pilot("lucia_denniard", Pilot.Status.internal, "Confederation of xXPIZZAXx", "Love Squad", "Lucia Denniard", "lucia@pizza.moe", Pilot.OM.createObjectNode(), List(), List(), List())
      lud.addUser(p, "luciapassword") must equal(true)
      val r = lud.getUser("lucia_denniard")
      val p2 = p.copy(alliance = "No Alliance")
      lud.updateUser(p2) must equal(true)
      lud.getUser("lucia_denniard") must equal(Some(p2))
      val p3 = p2.copy(corporation = "nocorp")
      lud.updateUser(p3) must equal(true)
      lud.getUser("lucia_denniard") must equal(Some(p3))
      val p4 = p3.copy(email = "otheremail")
      lud.updateUser(p4)
      lud.getUser("lucia_denniard") must equal(Some(p4))
      val p5 = p4.copy(accountStatus = Pilot.Status.ineligible)
      lud.updateUser(p5)
      lud.getUser("lucia_denniard") must equal(Some(p5))
      val p6 = p5.copy(authGroups = List("onegroup"))
      lud.updateUser(p6) must equal(true)
      lud.getUser("lucia_denniard") must equal(Some(p6))
      val p7 = p6.copy(authGroups = List("othergroup"))
      lud.updateUser(p7)
      lud.getUser("lucia_denniard") must equal(Some(p7))
      val p8 = p7.copy(crestTokens = List("mycresttoken"))
      lud.updateUser(p8)
      lud.getUser("lucia_denniard") must equal(Some(p8))
      val p9 = p8.copy(crestTokens = List("newcresttoken"))
      lud.updateUser(p9)
      lud.getUser("lucia_denniard") must equal(Some(p9))
      val p10 = p9.copy(apiKeys = List("apikey"))
      lud.updateUser(p10)
      lud.getUser("lucia_denniard") must equal(Some(p10))
      val p11 = p10.copy(apiKeys = List("newerapikey"))
      lud.updateUser(p11)
      lud.getUser("lucia_denniard") must equal(Some(p11))
      val p12 = p11.copy(metadata = Pilot.OM.readTree("{\"value\":\"imcool\"}"))
      lud.updateUser(p12)
      lud.getUser("lucia_denniard") must equal(Some(p12))
      server.stop()
    } finally {
      tempfolder.delete()
    }
  }


}
