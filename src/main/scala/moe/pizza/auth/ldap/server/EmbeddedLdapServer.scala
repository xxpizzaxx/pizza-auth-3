package moe.pizza.auth.ldap.server

import java.io.{File, IOException}
import javax.naming.NamingException

import org.apache.directory.api.ldap.model.entry.Entry
import org.apache.directory.api.ldap.model.exception.{LdapInvalidDnException, LdapException}
import org.apache.directory.api.ldap.model.name.Dn
import org.apache.directory.api.ldap.model.schema.SchemaObject
import org.apache.directory.api.ldap.model.schema.parsers.OpenLdapSchemaParser
import org.apache.directory.server.core.api.{InstanceLayout, DirectoryService}
import org.apache.directory.server.core.factory.DefaultDirectoryServiceFactory
import org.apache.directory.server.core.partition.impl.avl.AvlPartition
import org.apache.directory.server.ldap.LdapServer
import org.apache.directory.server.protocol.shared.store.LdifFileLoader
import org.apache.directory.server.protocol.shared.transport.TcpTransport
import org.slf4j.{LoggerFactory, Logger}


class EmbeddedLdapServer(instancePath: String, basedn: String, host: String, port: Int, instanceName: String = "pizza-auth-ldap") {
  private final val log: Logger = LoggerFactory.getLogger(getClass)
  private var directoryService: DirectoryService = null
  private var ldapService: LdapServer = null

  private val schemaParser = new OpenLdapSchemaParser
  val pizzaSchema = schemaParser.parse(scala.io.Source.fromInputStream(getClass.getResourceAsStream("/schemas/pizza.schema")).getLines().mkString("\n"))

  try {
    init
  }
  catch {
    case e: IOException => {
      log.error("IOException while initializing EmbeddedLdapServer", e)
    }
    case e: LdapException => {
      log.error("LdapException while initializing EmbeddedLdapServer", e)
    }
    case e: NamingException => {
      log.error("NamingException while initializing EmbeddedLdapServer", e)
    }
    case e: Exception => {
      log.error("Exception while initializing EmbeddedLdapServer", e)
    }
  }

  @throws(classOf[Exception])
  @throws(classOf[IOException])
  @throws(classOf[LdapException])
  @throws(classOf[NamingException])
  private def init() {
    val factory: DefaultDirectoryServiceFactory = new DefaultDirectoryServiceFactory
    factory.init(instanceName)
    directoryService = factory.getDirectoryService
    directoryService.getChangeLog.setEnabled(false)
    directoryService.setShutdownHookEnabled(true)
    val sm = directoryService.getSchemaManager
    sm.add(pizzaSchema)
    log.info("registered pizza schema")
    val il: InstanceLayout = new InstanceLayout(instancePath)
    directoryService.setInstanceLayout(il)
    val partition: AvlPartition = new AvlPartition(directoryService.getSchemaManager)
    partition.setId(instanceName)
    partition.setSuffixDn(new Dn(directoryService.getSchemaManager, basedn))
    partition.initialize
    directoryService.addPartition(partition)
    ldapService = new LdapServer
    ldapService.setTransports(new TcpTransport(host, port))
    ldapService.setDirectoryService(directoryService)
  }

  @throws(classOf[Exception])
  def start() {
    if (ldapService.isStarted) {
      throw new IllegalStateException("Service already running")
    }
    directoryService.startup
    ldapService.start
  }

  @throws(classOf[Exception])
  def stop() {
    if (!ldapService.isStarted) {
      throw new IllegalStateException("Service is not running")
    }
    ldapService.stop
    directoryService.shutdown
  }

  @throws(classOf[Exception])
  def applyLdif(ldifFile: File) {
    new LdifFileLoader(directoryService.getAdminSession, ldifFile, null).execute
  }

  @throws(classOf[LdapException])
  @throws(classOf[LdapInvalidDnException])
  def createEntry(id: String, attributes: java.util.Map[String, String]) {
    if (!ldapService.isStarted) {
      throw new IllegalStateException("Service is not running")
    }
    val dn: Dn = new Dn(directoryService.getSchemaManager, id)
    if (!directoryService.getAdminSession.exists(dn)) {
      val entry: Entry = directoryService.newEntry(dn)
      import scala.collection.JavaConversions._
      for (attributeId <- attributes.keySet) {
        entry.add(attributeId, attributes.get(attributeId))
      }
      directoryService.getAdminSession.add(entry)
    }
  }
}