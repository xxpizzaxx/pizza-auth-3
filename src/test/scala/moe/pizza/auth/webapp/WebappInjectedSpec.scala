package moe.pizza.auth.webapp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import moe.pizza.auth.config.ConfigFile.ConfigFile
import moe.pizza.auth.webapp.WebappTestSupports._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}
import org.mockito.Mockito.{when, verify, never, reset, times}
import org.mockito.Matchers.{anyString, anyInt}
import spark._

import scala.io.Source

/**
  * Created by Andi on 19/02/2016.
  */
class WebappInjectedSpec extends FlatSpec with MustMatchers with MockitoSugar {

  val ACCEPTHTML = "text/html"


  val OM = new ObjectMapper(new YAMLFactory())
  OM.registerModule(DefaultScalaModule)

  "Webapp" should "serve the landing page" in {
    withPort { port =>
      val config = Source.fromURL(getClass.getResource("/config.yml")).getLines().mkString("\n")
      val conf = OM.readValue[ConfigFile](config, classOf[ConfigFile])
      val w = new Webapp(conf, port)
      w.start()
      val handler = reflectRoutingTable().findTargetForRequestedRoute(spark.route.HttpMethod.get, "/", ACCEPTHTML)
      val req = mock[Request]
      val session = mock[Session]
      when(req.session()).thenReturn(session)
      when(session.attribute(Webapp.SESSION)).thenReturn(null)
      val resp = mock[Response]
      val res = handler.handle[String](req, resp)
      res.trim must equal(templates.html.base.apply("pizza-auth-3", templates.html.landing.apply(), None).toString().trim)
      Spark.stop()
    }
  }

}
