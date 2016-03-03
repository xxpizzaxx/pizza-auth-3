package moe.pizza.auth.graphdb

import java.io.{File, InputStreamReader, BufferedReader}
import java.util

import com.github.tototoshi.csv._
import com.orientechnologies.orient.`object`.db.OObjectDatabaseTx
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.{OResultSet, OSQLSynchQuery}
import com.tinkerpop.blueprints.impls.orient.{OrientVertex, OrientGraphFactory, OrientGraph}
import scala.collection.JavaConverters._


/**
  * Created by andi on 03/03/16.
  */
object CsvLoader extends App {

  val orient = new EmbeddedOrient
  orient.start(new File(".", "orient"), "verysecurepassword")

  val systems = CSVReader.open(
    new BufferedReader(
      new InputStreamReader(
        getClass().getResourceAsStream("/databases/systems.csv")))).allWithHeaders()
  val jumps = CSVReader.open(
    new BufferedReader(
      new InputStreamReader(getClass().getResourceAsStream("/databases/jumps.csv")))).allWithHeaders()

  //val uri: String = "root:verysecurepassword/map"

  val graphfactory = new OrientGraphFactory("plocal:map")

  def withGraph(f: (OrientGraph => Any)): Unit = {
    val t = graphfactory.getTx
    f(t)
    t.commit()
    t.shutdown()
  }

  withGraph { graph =>
    if (false) {
      try {
        val solarsystem = graph.createVertexType("solarsystem")
        solarsystem.createProperty("solarSystemName", OType.STRING)
        solarsystem.createProperty("solarSystemID", OType.INTEGER)

        graph.setUseLightweightEdges(true)
        val gate = graph.createEdgeType("gate")
      } finally {
        graph.rollback()
      }
    }
  }

  val lookup = new util.HashMap[Int, OrientVertex]()

  withGraph { graph =>
    for (system <- systems) {
      val s = graph.addVertex("class:solarsystem", Seq():_*)
      s.setProperty("solarSystemName", system("solarSystemName"))
      s.setProperty("solarSystemID", system("solarSystemID"))
      lookup.put(system("solarSystemID").toInt, s)
    }
  }


  for (gates <- jumps.grouped(100)) {
    withGraph { graph =>
      for (gate <- gates) {
        val from = gate("fromSolarSystemID").toInt
        val to = gate("toSolarSystemID").toInt
        val f = lookup.get(from)
        val t = lookup.get(to)
        val e = graph.addEdge("class:gate", f, t, "gate")
      }
    }
  }

  implicit class dbWrapper(db: ODatabaseDocumentTx) {
      def queryBySql[T](sql: String, params: AnyRef*): List[T] = {
          val params4java = params.toArray
          val results: java.util.List[T] = db.query(new OSQLSynchQuery[T](sql), params4java: _*)
          results.asScala.toList
      }
  }

  withGraph { graph =>
    val amarr = graph.getVertices("solarSystemName", "Amarr").iterator().next().getId.toString
    val jita = graph.getVertices("solarSystemName", "Jita").iterator().next().getId.toString
    println(amarr)
    val r = graph.getRawGraph.queryBySql[AnyRef](s"select flatten(shortestPath($amarr, $jita, 'BOTH', 'gate'))")
    val r2 = graph.getRawGraph.queryBySql[AnyRef](s"select flatten(shortestPath($jita, $amarr, 'BOTH', 'gate'))")
    val r3 = graph.getRawGraph.queryBySql[AnyRef](s"select both() from $amarr")
    //val jitatoamarr = graph.getRawGraph.query(new OSQLSynchQuery[OResultSet[Any]](s"select shortestPath(${amarr}, ${jita})"))
    println("done")
  }








}
