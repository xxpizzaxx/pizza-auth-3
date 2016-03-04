package moe.pizza.auth.adapters

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import moe.pizza.auth.adapters.PilotGraderLike.PilotGraderFactory
import moe.pizza.auth.config.ConfigFile.AuthConfig
import moe.pizza.auth.plugins.pilotgraders.{AlliedPilotGrader, CrestKeyGrader}
import moe.pizza.auth.plugins.pilotgraders.MembershipPilotGraders.{CorporationPilotGrader, AlliancePilotGrader}
import moe.pizza.auth.webapp.WebappTestSupports
import org.scalatest.{MustMatchers, WordSpec}

import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Created by Andi on 28/02/2016.
  */
class PilotGraderLikeSpec extends WordSpec with MustMatchers {

  val OM = new ObjectMapper(new YAMLFactory())
  OM.registerModule(DefaultScalaModule)

  import PilotGraderLike.PilotGraderLike
  def create[T](j: JsonNode)(implicit pg: PilotGraderLike[T]): T = pg(j, configfile)

  val configfile = WebappTestSupports.readTestConfig()

  "PilotGraderLike" when {
    "constructing PilotGraders with TypeClasses" should {
      "create an AlliancePilotGrader from YAML" in {
        val config = """---
          |  AlliancePilotGrader:
          |    alliance: "Confederation of xXPIZZAXx"
        """.stripMargin
        val c = OM.readTree(config).get("AlliancePilotGrader")
        val result = create[AlliancePilotGrader](c)
      }
      "create a CorporationPilotGrader from YAML" in {
        val config = """---
          |  CorporationPilotGrader:
          |    corporation: "Love Squad"
        """.stripMargin
        val c = OM.readTree(config).get("CorporationPilotGrader")
        val result = create[CorporationPilotGrader](c)
      }
      "create a CrestKeyGrader from YAML" in {
        val config = """---
          |  CrestKeyGrader:
          |    clientID: "whatever"
          |    secretKey: "also whatever"
          |    redirectUrl: "http://whatever.com/whatever"
        """.stripMargin
        val c = OM.readTree(config).get("CrestKeyGrader")
        val result = create[CrestKeyGrader](c)
      }
      "create an AlliedPilotGrader from YAML" in {
        val config = """---
          |  AlliedPilotGrader:
          |    keyId: 400
          |    vCode: "whatever"
          |    threshold: 4.5
          |    usecorp: false
        """.stripMargin
        val c = OM.readTree(config).get("AlliedPilotGrader")
        val result = create[AlliedPilotGrader](c)
      }
    }
    "when constructing PilotGraders from pure YAML" should {
      val fullconfig =
        """---
          |  -
          |    type: "AlliancePilotGrader"
          |    alliance: "Confederation of xXPIZZAXx"
          |  -
          |    type: "CorporationPilotGrader"
          |    corporation: "Love Squad"
          |  -
          |    type: "CrestKeyGrader"
          |    clientID: "whatever"
          |    secretKey: "also whatever"
          |    redirectUrl: "http://whatever.com/whatever"
          |  -
          |    type: "AlliedPilotGrader"
          |    keyId: 400
          |    vCode: "whatever"
          |    threshold: 4.5
          |    usecorp: false
          |  -
          |    nothing: "nope"
          |  -
          |    type: "idontexist"
          |    fakedata: "nope"
          |  -
          |    type: AlliancePilotGrader
          |  -
          |    type: CorporationPilotGrader
          |  -
          |    type: CrestKeyGrader
        """.stripMargin
      val parsedconfig = OM.readTree(fullconfig)


      "create an AlliancePilotGrader from YAML" in {
        val r = PilotGraderFactory.fromYaml(parsedconfig.get(0), configfile)
      }
      "create a CorporationPilotGrader from YAML" in {
        val r = PilotGraderFactory.fromYaml(parsedconfig.get(1), configfile)
      }
      "create a CrestKeyGrader from YAML" in {
        val r = PilotGraderFactory.fromYaml(parsedconfig.get(2), configfile)
      }
      "create an AlliedPilotGrader from YAML" in {
        val r = PilotGraderFactory.fromYaml(parsedconfig.get(3), configfile)
      }
      "fail gracefully when there is no type" in {
        PilotGraderFactory.fromYaml(parsedconfig.get(4), configfile) must equal(None)
      }
      "fail gracefully when the type doesn't have a mapping from YAML" in {
        PilotGraderFactory.fromYaml(parsedconfig.get(5), configfile) must equal(None)
      }
      "create an AlliancePilotGrader with fallbacks" in {
        PilotGraderFactory.fromYaml(parsedconfig.get(6), configfile)
      }
      "create a CorporationPilotGrader with fallbacks" in {
        PilotGraderFactory.fromYaml(parsedconfig.get(7), configfile)
      }
      "create a CrestKeyGrader with fallbacks" in {
        PilotGraderFactory.fromYaml(parsedconfig.get(8), configfile)
      }

      "create a set of PilotGraders from YAML" in {
        import scala.collection.JavaConverters._
        val graders = parsedconfig.iterator().asScala.toList
        graders.map(g => PilotGraderFactory.fromYaml(g, configfile)).flatten.toList.size must equal(7)
      }

      "create a set of PilotGraders from YAML via the config object" in {
        import scala.collection.JavaConverters._
        val c = new AuthConfig(null, null, null, null, null, parsedconfig.iterator().asScala.toList)
        c.constructGraders(configfile) must not equal(null)
      }

    }
  }

}
