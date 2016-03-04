package moe.pizza.auth.adapters

import moe.pizza.auth.interfaces.UserDatabase
import moe.pizza.auth.models.Pilot
import moe.pizza.auth.ldap.client.{LdapClient, Utils}
import org.apache.directory.api.ldap.model.entry.{ModificationOperation, DefaultModification, DefaultEntry}
import org.apache.directory.api.ldap.model.message._
import org.apache.directory.api.ldap.model.name.Dn
import org.apache.directory.api.ldap.model.schema.SchemaManager
import LdapClient._


/**
  * Created by andi on 03/03/16.
  */
class LdapUserDatabase(client: LdapClient, schema: SchemaManager) extends UserDatabase {

  private def makePassword(p: String) = Utils.hashPassword(p)

  implicit class ConvertablePilot(p: Pilot) {
    def toAddRequest(s: SchemaManager, password: String) = {
      val e = new DefaultEntry(s,
        s"uid=${p.uid},ou=pizza",
        "ObjectClass: top",
        "ObjectClass: pilot",
        "ObjectClass: account",
        "ObjectClass: simpleSecurityObject",
        s"corporation: ${p.corporation}",
        s"alliance: ${p.alliance}",
        s"accountStatus: ${p.accountStatus.toString}",
        s"email: ${p.email}",
        s"uid: ${p.uid}",
        s"metadata: ${p.metadata.toString}",
        s"characterName: ${p.characterName}"
      )
      e.add("crestToken", p.crestTokens:_*)
      e.add("userpassword", makePassword(password))
      val add = new AddRequestImpl()
      add.setEntry(e)
      add
    }
  }

  override def addUser(p: Pilot, password: String): Boolean = {
    var res = false
    client.withConnection{ c =>
      val r = c.add(p.toAddRequest(schema, password))
      if (r.getLdapResult.getResultCode == ResultCodeEnum.SUCCESS)
        res = true
    }
    res
  }

  override def deleteUser(p: Pilot): Boolean = {
    client.withConnection { c =>
      val req = new DeleteRequestImpl
      req.setName(new Dn(s"uid=${p.uid},ou=pizza"))
      c.delete(req).getLdapResult.getResultCode == ResultCodeEnum.SUCCESS
    }
  }

  override def authenticateUser(uid: String, password: String): Option[Pilot] = {
    val success = client.withConnection { c =>
      val bindreq = new BindRequestImpl()
      bindreq.setDn(new Dn(s"uid=$uid,ou=pizza"))
      bindreq.setCredentials(password)
      val r = c.bind(bindreq)
      r.getLdapResult.getResultCode == ResultCodeEnum.SUCCESS
    }
    if (success) {
      getUser(uid)
    } else {
      None
    }
  }

  override def getUser(uid: String): Option[Pilot] = {
    client.withConnection { connection =>
      connection.filter("ou=pizza", s"(&(uid=$uid)(objectclass=pilot))").toList.headOption.map(_.toMap).map(Pilot.fromMap)
    }
  }

  override def getUsers(filter: String): List[Pilot] = {
    client.withConnection{ c =>
      c.filter("ou=pizza", s"(&(objectclass=pilot)($filter))").toList.map(_.toMap).map(Pilot.fromMap)
    }
  }

  override def updateUser(p: Pilot): Boolean = true

  override def setPassword(p: Pilot, password: String): Boolean = {
    client.withConnection{ c =>
      val mod = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "userPassword", makePassword(password))
      val modrequest = new ModifyRequestImpl
      modrequest.addModification(mod)
      modrequest.setName(new Dn(s"uid=${p.uid},ou=pizza"))
      val res = c.modify(modrequest)
      if (res.getLdapResult.getResultCode == ResultCodeEnum.SUCCESS) {
        true
      } else {
        false
      }
    }
  }

  override def getAllUsers(): Seq[Pilot] = {
    client.withConnection{ connection =>
      connection.filter("ou=pizza", "(objectclass=pilot)").toSeq.map(_.toMap).map(Pilot.fromMap)
    }
  }
}
