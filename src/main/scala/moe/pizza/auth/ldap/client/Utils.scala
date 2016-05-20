package moe.pizza.auth.ldap.client

import org.apache.directory.api.ldap.model.constants.LdapSecurityConstants
import org.apache.directory.api.ldap.model.password.PasswordUtil

/**
  * Created by Andi on 20/02/2016.
  */
object Utils {
  def hashPassword(p: String) = {
    PasswordUtil.createStoragePassword(p.getBytes, LdapSecurityConstants.HASH_METHOD_SSHA)
  }
  def testPasword(p: String, hash: Array[Byte]): Boolean = {
    PasswordUtil.compareCredentials(p.getBytes, hash)
  }
}
