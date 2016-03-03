package moe.pizza.auth.graphdb

import java.io.{BufferedReader, File, InputStreamReader}
import java.util

import com.github.tototoshi.csv._
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.tinkerpop.blueprints.impls.orient.{OrientGraph, OrientGraphFactory, OrientGraphNoTx, OrientVertex}
import org.apache.commons.io.FileUtils

import scala.collection.JavaConverters._


class EveMapDb(dbname: String = "map") {

  val graphfactory = new OrientGraphFactory(s"plocal:$dbname")

  def withGraph[T](f: (OrientGraph => T)): T = {
    val t = graphfactory.getTx
    val r = f(t)
    t.commit()
    t.shutdown()
    r
  }

  def withGraphNoTx[T](f: (OrientGraphNoTx => T)): T = {
    val t = graphfactory.getNoTx
    val r = f(t)
    t.commit()
    t.shutdown()
    r
  }

  def provisionIfRequired() = {
    var provision: Boolean = false
    withGraph { graph =>
      // if the database doesn't look provisioned, provision it
      if (graph.getEdgeType("gate") == null)
        provision = true
    }
    if (provision) {
      initialize()
    }
  }


  def initialize() {
    val systems = CSVReader.open(
      new BufferedReader(
        new InputStreamReader(
          getClass().getResourceAsStream("/databases/systems.csv")))).allWithHeaders()
    val jumps = CSVReader.open(
      new BufferedReader(
        new InputStreamReader(getClass().getResourceAsStream("/databases/jumps.csv")))).allWithHeaders()

    withGraphNoTx { graph =>
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

    val lookup = new util.HashMap[Int, OrientVertex]()

    withGraph { graph =>
      for (system <- systems) {
        val s = graph.addVertex("class:solarsystem", Seq(): _*)
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
  }

  implicit class dbWrapper(db: ODatabaseDocumentTx) {
    def queryBySql[T](sql: String, params: AnyRef*): List[T] = {
      val params4java = params.toArray
      val results: java.util.List[T] = db.query(new OSQLSynchQuery[T](sql), params4java: _*)
      results.asScala.toList
    }
  }

  def getDistanceBetweenSystemsByName(s1: String, s2: String): Option[Int] = {
    if (s1==s2) {
      return Some(0)
    }
    withGraph { graph =>
      val s1v = graph.getVertices("solarSystemName", s1).iterator().asScala.toList.headOption.map{_.getId.toString}
      val s2v = graph.getVertices("solarSystemName", s2).iterator().asScala.toList.headOption.map(_.getId.toString)
      if (s1v.isDefined && s2v.isDefined) {
        val result = graph.getRawGraph.queryBySql[ODocument](s"select flatten(shortestPath(${s1v.get}, ${s2v.get}, 'BOTH', 'gate'))")
        Some(result.size)
      } else {
        None
      }
    }
  }

  def getDistanceBetweenSystemsById(s1: Int, s2: Int): Option[Int] = {
    if (s1==s2) {
      return Some(0)
    }
    withGraph { graph =>
      val s1v = graph.getVertices("solarSystemID", s1).iterator().asScala.toList.headOption.map{_.getId.toString}
      val s2v = graph.getVertices("solarSystemID", s2).iterator().asScala.toList.headOption.map(_.getId.toString)
      if (s1v.isDefined && s2v.isDefined) {
        val result = graph.getRawGraph.queryBySql[ODocument](s"select flatten(shortestPath(${s1v.get}, ${s2v.get}, 'BOTH', 'gate'))")
        Some(result.size)
      } else {
        None
      }
    }
  }

  def cleanUp() = {
    graphfactory.close()
    graphfactory.drop()
    FileUtils.deleteDirectory(new File(".", dbname))
  }

}
