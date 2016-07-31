package moe.pizza.auth.bots

import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.Stanza
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}
import org.mockito.Mockito._
import org.mockito.Matchers._

class XmppBotSpec extends FlatSpec with MustMatchers with MockitoSugar {

  "XmppBot" should "send PMs" in {
    val bot = new XmppBot
    val con = mock[XMPPConnection]
    bot.connection = Some(con)
    bot.sendMessage("hello@test.site", "message here")
    verify(con, times(1)).sendStanza(anyObject())
  }

}
