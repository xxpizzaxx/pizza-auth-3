package moe.pizza.auth.ldap.server

import org.apache.directory.api.ldap.model.entry.{Entry, Attribute}
import org.apache.directory.api.ldap.model.message.SearchScope
import org.apache.directory.ldap.client.api._

import scala.collection.JavaConverters._

/**
  * Created by Andi on 18/02/2016.
  */
class LdapClient(host: String, port: Int, user: String, password: String) {
  val config = new LdapConnectionConfig()
  config.setLdapHost(host)
  config.setLdapPort(port)
  config.setName(user)
  config.setCredentials(password)
  val factory = new DefaultPoolableLdapConnectionFactory(config)
  val pool = new LdapConnectionPool(factory)
  pool.setTestOnBorrow(true)


  implicit class PimpedEntry(e: Entry) {
    def toMap = {
      e.getAttributes.asScala.toList.map(e =>
        e.get().getValue match {
          case s: String => (e.getId, s)
          case _ => (e.getId, "UNKNOWN")
        }
      ).groupBy{_._1}.mapValues(_.map{_._2})
    }
  }

  implicit class PimpedLdapConnection(c: LdapConnection) {
    def filter(parent: String, filter: String) = {
      val p = c.search(parent, filter, SearchScope.SUBTREE)
      p.iterator().asScala
    }
  }

  def withConnection(block: LdapConnection => Unit) {
    val con = pool.getConnection
    try {
      block(con)
    } finally {
      pool.releaseConnection(con)
    }
  }


}
