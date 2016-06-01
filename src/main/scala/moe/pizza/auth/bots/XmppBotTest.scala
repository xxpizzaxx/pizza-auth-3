package moe.pizza.auth.bots

/**
  * Created by Andi on 01/06/2016.
  */
object XmppBotTest extends App {

  val bot = new XmppBot()
  bot.connect("test", "test", "test")
  bot.sendMessage("liara_denniard@luv2.faith", "hi")
  readLine()

}
