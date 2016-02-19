package moe.pizza.auth.queue

import java.util.Properties

import org.apache.zookeeper.server.{ServerConfig, ZooKeeperServerMain}
import org.apache.zookeeper.server.quorum.QuorumPeerConfig

/**
  * Created by andi on 19/02/16.
  */
class EmbeddedZookeeper(config: Properties) {
  val quorumconfig = new QuorumPeerConfig
  quorumconfig.parseProperties(config)

  val server = new ZooKeeperServerMain
  val zkconfig = new ServerConfig
  zkconfig.readFrom(quorumconfig)

  var zkthread: Option[Thread] = None

  def start(): Thread = {
    val thread = new Thread() {
      override def run(): Unit = {
        try {
          server.runFromConfig(zkconfig)
        } catch {
          case e: Throwable => e.printStackTrace(System.err)
        }

      }
    }
    thread.start()
    zkthread = Some(thread)
    thread
  }

  def stop() = {
    zkthread match {
      case Some(t) => t.destroy()
      case None => ()
    }
  }


}
