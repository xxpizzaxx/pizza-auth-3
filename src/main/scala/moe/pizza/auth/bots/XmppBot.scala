package moe.pizza.auth.bots

import org.jivesoftware.smack.chat.ChatManager
import org.jivesoftware.smack.tcp.{
  XMPPTCPConnection,
  XMPPTCPConnectionConfiguration
}
import org.jivesoftware.smack.{ConnectionConfiguration, XMPPConnection}

/**
  * Created by Andi on 01/06/2016.
  */
class XmppBot {

  var connection: Option[XMPPConnection] = None

  def connect(uid: String, host: String, password: String) = {
    val config = XMPPTCPConnectionConfiguration
      .builder()
      .setUsernameAndPassword(uid, password)
      .setServiceName(host)
      .setHost(host)
      .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
      .build()
    val con = new XMPPTCPConnection(config)
    con.connect()
    con.login()
    connection = Some(con)
  }

  def sendMessage(jid: String, message: String) = {
    connection match {
      case Some(c) =>
        val cm = ChatManager.getInstanceFor(c)
        val chat = cm.createChat(jid)
        chat.sendMessage(message)
        chat.close()
      case None => ()
    }
  }

}
