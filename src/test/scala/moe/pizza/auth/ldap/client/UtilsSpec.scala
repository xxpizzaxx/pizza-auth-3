package moe.pizza.auth.ldap.client

import org.scalacheck.Properties
import org.scalacheck.Prop.forAll

object UtilsSpec extends Properties("LdapClientUtils") {

  property("hashing") = forAll { (p: String) =>
    Utils.hashPassword(p).length > 0
  }

  property("two-way hashing") = forAll { (p: String) =>
    Utils.testPasword(p, Utils.hashPassword(p))
  }

}
