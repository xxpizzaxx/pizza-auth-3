package moe.pizza.auth.models

import java.io.File
import java.util.UUID

import moe.pizza.auth.ldap.client.LdapClient
import moe.pizza.auth.ldap.server.EmbeddedLdapServer
import org.apache.directory.api.ldap.model.entry.{Attribute, DefaultAttribute, DefaultEntry}
import org.apache.directory.api.ldap.model.name.Dn
import org.scalatest.{FlatSpec, MustMatchers}

/**
  * Created by andi on 18/02/16.
  */
class UserLoadingSpec extends FlatSpec with MustMatchers {

  def createTempFolder(suffix: String): File = {
    val base = new File(new File(System.getProperty("java.io.tmpdir")), "pizza-auth-test")
    base.delete()
    val dir = new File(base, UUID.randomUUID().toString + suffix)
    dir.mkdirs()
    dir
  }

  "loading a user out of LDAP" should "extract correctly" in {
    val tempfolder = createTempFolder("loadusertest")
    try {
      val server = new EmbeddedLdapServer(tempfolder.toString, "ou=pizza", "localhost", 3389)
      server.setPassword("testpassword")
      server.start()
      val schema = server.directoryService.getSchemaManager
      val e = new DefaultEntry(schema, "uid=lucia_denniard,ou=pizza",
        "ObjectClass: top",
        "ObjectClass: pilot",
        "ObjectClass: simpleSecurityObject",
        "objectclass: account",
        "accountStatus: Internal",
        "alliance: Confederation of xXPIZZAXx",
        "characterName: Lucia Denniard",
        "corporation: Love Squad",
        "email: lucia@pizza.moe",
        "metadata: {}",
        "uid: lucia_denniard",
        "userpassword: blah"
      )
      server.createEntry("uid=lucia_denniard,ou=pizza", e)

      // use the client
      val c = new LdapClient("localhost", 3389, "uid=admin,ou=system", "testpassword")
      c.withConnection { con =>
        import c._
        val r = con.filter("ou=pizza", "(uid=lucia_denniard)")
        val user = r.toList.headOption.map(_.toMap).map(Pilot.fromMap)
        user must equal(Some(Pilot("lucia_denniard", Pilot.Status.internal, "Confederation of xXPIZZAXx", "Love Squad", "Lucia Denniard", "lucia@pizza.moe", Pilot.OM.createObjectNode(), List(), List(), List())))
      }
      server.stop()
    } finally {
      tempfolder.delete()

    }

  }

}
