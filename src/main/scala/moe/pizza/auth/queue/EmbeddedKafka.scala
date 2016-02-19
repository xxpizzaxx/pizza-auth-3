package moe.pizza.auth.queue

import java.util.Properties

import kafka.server.{KafkaConfig, KafkaServerStartable}

/**
  * Created by andi on 19/02/16.
  */
class EmbeddedKafka(kafkaconfig: Properties, zkconfig: Properties) {

  val kafka = new KafkaServerStartable(new KafkaConfig(kafkaconfig))
  val zookeeper = new EmbeddedZookeeper(zkconfig)

  def start() = {
    kafka.startup()
    zookeeper.start()
  }

  def stop() = {
    kafka.shutdown()
    kafka.awaitShutdown()
    zookeeper.stop()
  }

}
