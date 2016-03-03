package moe.pizza.auth.graphdb

import java.io.File

import com.orientechnologies.orient.server.config.OServerConfiguration
import com.orientechnologies.orient.server.{OServerMain, OServer}

/**
  * Created by andi on 03/03/16.
  */
class EmbeddedOrient {


  var server: OServer = null

  def start(home: File, pw: String): Unit = {
    val cfg = new OServerConfiguration()
    val orientdbHome = home.getAbsolutePath
    System.setProperty("ORIENTDB_HOME", orientdbHome);
    server = OServerMain.create()
    server.startup(
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<orient-server>"
        + "<network>"
        + "<protocols>"
        + "<protocol name=\"binary\" implementation=\"com.orientechnologies.orient.server.network.protocol.binary.ONetworkProtocolBinary\"/>"
        + "<protocol name=\"http\" implementation=\"com.orientechnologies.orient.server.network.protocol.http.ONetworkProtocolHttpDb\"/>"
        + "</protocols>"
        + "<listeners>"
        + "<listener ip-address=\"127.0.0.1\" port-range=\"2424-2430\" protocol=\"binary\"/>"
        + "<listener ip-address=\"127.0.0.1\" port-range=\"2480-2490\" protocol=\"http\"/>"
        + "</listeners>"
        + "</network>"
        + "<users>"
        + s"""<user name="root" password="${pw}" resources="*"/>"""
        + "</users>"
        + "<properties>"
        + "<entry name=\"orientdb.www.path\" value=\"C:/work/dev/orientechnologies/orientdb/releases/1.0rc1-SNAPSHOT/www/\"/>"
        + "<entry name=\"orientdb.config.file\" value=\"C:/work/dev/orientechnologies/orientdb/releases/1.0rc1-SNAPSHOT/config/orientdb-server-config.xml\"/>"
        + "<entry name=\"server.cache.staticResources\" value=\"false\"/>"
        + "<entry name=\"log.console.level\" value=\"info\"/>"
        + "<entry name=\"log.file.level\" value=\"fine\"/>"
        //The following is required to eliminate an error or warning "Error on resolving property: ORIENTDB_HOME"
        + "<entry name=\"plugin.dynamic\" value=\"false\"/>"
        + "</properties>" + "</orient-server>")
    server.activate()
  }

  def stop(): Unit = {
    server.shutdown()
  }

}
