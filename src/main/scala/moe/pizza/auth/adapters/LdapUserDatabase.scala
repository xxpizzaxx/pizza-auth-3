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
    Option(client.withConnection { c =>
      val bindreq = new BindRequestImpl()
      bindreq.setDn(new Dn(s"uid=$uid,ou=pizza"))
      bindreq.setCredentials(password)
      val r = c.bind(bindreq)
      r.getLdapResult.getResultCode == ResultCodeEnum.SUCCESS
    })
      .filter(_==true)
      .flatMap(_ => getUser(uid))
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

  override def updateUser(p: Pilot): Boolean = {
    val op = getUser(p.uid).get
    client.withConnection { c =>
      val modrequest = new ModifyRequestImpl
      if (p.corporation != op.corporation) {
        modrequest.addModification(
          new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "corporation", p.corporation)
        )
      }
      if (p.alliance != op.alliance) {
        modrequest.addModification(
          new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "alliance", p.alliance)
        )
      }
      if (p.accountStatus != op.accountStatus) {
        modrequest.addModification(
          new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "accountStatus", p.accountStatus.toString)
        )
      }
      if (p.email != op.email) {
        modrequest.addModification(
          new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "email", p.email)
        )
      }
      if (p.characterName != op.characterName) {
        modrequest.addModification(
          new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "characterName", p.characterName)
        )
      }
      if (p.metadata.toString != op.metadata.toString) {
        modrequest.addModification(
          new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "metadata", p.metadata.toString)
        )
      }
      if (p.authGroups != op.authGroups) {
        val newgroups = p.authGroups.toSet
        val oldgroups = op.authGroups.toSet
        val addme    = newgroups diff oldgroups
        val removeme = oldgroups diff newgroups
        for (a <- addme) {
          modrequest.addModification(
            new DefaultModification(ModificationOperation.ADD_ATTRIBUTE, "authGroup", a)
          )
        }
        for (r <- removeme) {
          modrequest.addModification(
            new DefaultModification(ModificationOperation.REMOVE_ATTRIBUTE, "authGroup", r)
          )
        }
      }
      if (p.crestTokens != op.crestTokens) {
        val newtokens = p.crestTokens.toSet
        val oldtokens = op.crestTokens.toSet
        val addme    = newtokens diff oldtokens
        val removeme = oldtokens diff newtokens
        for (a <- addme) {
          modrequest.addModification(
            new DefaultModification(ModificationOperation.ADD_ATTRIBUTE, "crestToken", a)
          )
        }
        for (r <- removeme) {
          modrequest.addModification(
            new DefaultModification(ModificationOperation.REMOVE_ATTRIBUTE, "crestToken", r)
          )
        }
      }
      if (p.apiKeys != op.apiKeys) {
        val newkeys = p.apiKeys.toSet
        val oldkeys = op.apiKeys.toSet
        val addme    = newkeys diff oldkeys
        val removeme = oldkeys diff newkeys
        for (a <- addme) {
          modrequest.addModification(
            new DefaultModification(ModificationOperation.ADD_ATTRIBUTE, "apiKey", a)
          )
        }
        for (r <- removeme) {
          modrequest.addModification(
            new DefaultModification(ModificationOperation.REMOVE_ATTRIBUTE, "apiKey", r)
          )
        }
      }
      modrequest.setName(new Dn(s"uid=${p.uid},ou=pizza"))
      val res = c.modify(modrequest)
      res.getLdapResult.getResultCode == ResultCodeEnum.SUCCESS
    }
  }

  override def setPassword(p: Pilot, password: String): Boolean = {
    client.withConnection{ c =>
      val mod = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "userPassword", makePassword(password))
      val modrequest = new ModifyRequestImpl
      modrequest.addModification(mod)
      modrequest.setName(new Dn(s"uid=${p.uid},ou=pizza"))
      val res = c.modify(modrequest)
      res.getLdapResult.getResultCode == ResultCodeEnum.SUCCESS
    }
  }

  override def getAllUsers(): Seq[Pilot] = {
    client.withConnection{ connection =>
      connection.filter("ou=pizza", "(objectclass=pilot)").toSeq.map(_.toMap).map(Pilot.fromMap)
    }
  }
}
