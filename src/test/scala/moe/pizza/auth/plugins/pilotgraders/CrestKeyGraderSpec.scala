package moe.pizza.auth.plugins.pilotgraders

import moe.pizza.auth.models.Pilot
import moe.pizza.crestapi.CrestApi
import moe.pizza.crestapi.CrestApi.{CallbackResponse, VerifyResponse}
import org.http4s.client.blaze.PooledHttp1Client
import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}

import scalaz.concurrent.Task


/**
  * Created by Andi on 28/02/2016.
  */
class CrestKeyGraderSpec extends FlatSpec with MustMatchers with MockitoSugar {
  implicit val client = PooledHttp1Client() //TODO: no mocking?

  "CrestKeyGrader" should "mark users as unclassified if their key is fine" in {
    val crest = mock[CrestApi]
    val p = Pilot("bob",
                  Pilot.Status.internal,
                  "myalliance",
                  "mycorp",
                  "Bob",
                  "bob@bob.com",
                  Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")),
                  List("group1", "group3"),
                  List("123:bobkey"),
                  List.empty)
    val c = new CrestKeyGrader(crest)
    when(crest.refresh("bobkey")).thenReturn(Task {
      new CallbackResponse("bobrefresh", null, 0, None)
    })
    when(crest.verify("bobrefresh")).thenReturn(Task {
      new VerifyResponse(123, "Bob", null, null, null, null, null)
    })
    c.grade(p) must equal(Pilot.Status.unclassified)
    verify(crest).refresh("bobkey")
    verify(crest).verify("bobrefresh")
  }

  "CrestKeyGrader" should "mark users as expired if their key was not good" in {
    val crest = mock[CrestApi]
    val p = Pilot("bob",
                  Pilot.Status.internal,
                  "myalliance",
                  "mycorp",
                  "Bob",
                  "bob@bob.com",
                  Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")),
                  List("group1", "group3"),
                  List("123:bobkey"),
                  List.empty)
    val c = new CrestKeyGrader(crest)
    when(crest.refresh("bobkey")).thenReturn(Task {
      throw new Exception("nope sorry")
    })
    c.grade(p) must equal(Pilot.Status.expired)
    verify(crest).refresh("bobkey")
  }

  "CrestKeyGrader" should "mark users as unclassified if they had one good key" in {
    val crest = mock[CrestApi]
    val p = Pilot("bob",
                  Pilot.Status.internal,
                  "myalliance",
                  "mycorp",
                  "Bob",
                  "bob@bob.com",
                  Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")),
                  List("group1", "group3"),
                  List("1:badkey", "123:bobkey"),
                  List.empty)
    val c = new CrestKeyGrader(crest)
    when(crest.refresh("bobkey")).thenReturn(Task {
      new CallbackResponse("bobrefresh", null, 0, None)
    })
    when(crest.refresh("badkey")).thenReturn(Task {
      throw new Exception("nope sorry")
    })
    when(crest.verify("bobrefresh")).thenReturn(Task {
      new VerifyResponse(123, "Bob", null, null, null, null, null)
    })
    c.grade(p) must equal(Pilot.Status.unclassified)
    verify(crest).refresh("bobkey")
    verify(crest).verify("bobrefresh")
  }

  "CrestKeyGrader" should "mark users as expired if their keys are good but none are for their main" in {
    val crest = mock[CrestApi]
    val p = Pilot("bob",
                  Pilot.Status.internal,
                  "myalliance",
                  "mycorp",
                  "Bob",
                  "bob@bob.com",
                  Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")),
                  List("group1", "group3"),
                  List("1:bobakey", "2:bobbkey"),
                  List.empty)
    val c = new CrestKeyGrader(crest)
    when(crest.refresh("bobakey")).thenReturn(Task {
      new CallbackResponse("bobarefresh", null, 0, None)
    })
    when(crest.refresh("bobbkey")).thenReturn(Task {
      new CallbackResponse("bobbrefresh", null, 0, None)
    })
    when(crest.verify("bobarefresh")).thenReturn(Task {
      new VerifyResponse(1, "BobA", null, null, null, null, null)
    })
    when(crest.verify("bobbrefresh")).thenReturn(Task {
      new VerifyResponse(2, "BobB", null, null, null, null, null)
    })
    c.grade(p) must equal(Pilot.Status.expired)
    verify(crest).refresh("bobakey")
    verify(crest).refresh("bobbkey")
    verify(crest).verify("bobarefresh")
    verify(crest).verify("bobbrefresh")
  }

}
