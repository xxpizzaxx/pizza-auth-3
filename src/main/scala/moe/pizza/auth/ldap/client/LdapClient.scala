package moe.pizza.auth.ldap.client

import org.apache.directory.api.ldap.model.entry.Entry
import org.apache.directory.api.ldap.model.message.SearchScope
import org.apache.directory.ldap.client.api._

import scala.collection.JavaConverters._


object LdapClient {
   implicit class PimpedEntry(e: Entry) {
    def toMap = {
      e.iterator().asScala.map(kv => (kv.getId, kv.iterator().asScala.toList.map(_.toString))).toMap
    }
  }

  implicit class PimpedLdapConnection(c: LdapConnection) {
    def filter(parent: String, filter: String) = {
      val p = c.search(parent, filter, SearchScope.SUBTREE)
      p.iterator().asScala
    }
  }
}

class LdapClient(host: String, port: Int, user: String, password: String) {
  val config = new LdapConnectionConfig()
  config.setLdapHost(host)
  config.setLdapPort(port)
  config.setName(user)
  config.setCredentials(password)
  val factory = new DefaultPoolableLdapConnectionFactory(config)
  val pool = new LdapConnectionPool(factory)
  pool.setTestOnBorrow(true)



  def withConnection[T](block: LdapConnection => T): T = {
    var result: Option[T] = None
    val con = pool.getConnection
    try {
      result = Some(block(con))
    } finally {
      pool.releaseConnection(con)
    }
    result.get
  }


}
