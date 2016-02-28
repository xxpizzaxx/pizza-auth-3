package moe.pizza.auth.plugins

import moe.pizza.auth.models.Pilot
import moe.pizza.crestapi.CrestApi
import moe.pizza.crestapi.CrestApi.{CallbackResponse, VerifyResponse}
import org.scalatest.{FlatSpec, MustMatchers}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito.{when, verify, never, reset, times, spy}
import org.mockito.Matchers.{anyString, anyInt}

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.util.Try

/**
  * Created by Andi on 28/02/2016.
  */
class CrestKeyGraderSpec extends FlatSpec with MustMatchers with MockitoSugar {

  "CrestKeyGrader" should "mark users as unclassified if their key is fine" in {
    val crest = mock[CrestApi]
    val p = Pilot("bob", Pilot.Status.internal, "myalliance", "mycorp", "Bob", "bob@bob.com", Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")), List("group1", "group3"), List("123:bobkey"), List.empty )
    val p2 = Pilot("terry", Pilot.Status.internal, "myalliance", "mycorp", "Terry", "bob@bob.com", Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")), List("group1", "group2"), List.empty, List.empty )
    val c = new CrestKeyGrader(crest)
    when(crest.refresh("bobkey")(global)).thenReturn(Future{new CallbackResponse("bobrefresh", null, 0, None)})
    when(crest.verify("bobrefresh")(global)).thenReturn(Future{new VerifyResponse(123, "Bob", null, null, null, null, null)})
    c.grade(p) must equal(Pilot.Status.unclassified)
    verify(crest).refresh("bobkey")
    verify(crest).verify("bobrefresh")
  }

  "CrestKeyGrader" should "mark users as expired if their key was not good" in {
    val crest = mock[CrestApi]
    val p = Pilot("bob", Pilot.Status.internal, "myalliance", "mycorp", "Bob", "bob@bob.com", Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")), List("group1", "group3"), List("123:bobkey"), List.empty )
    val c = new CrestKeyGrader(crest)
    when(crest.refresh("bobkey")(global)).thenReturn(Future{throw new Exception("nope sorry")})
    c.grade(p) must equal(Pilot.Status.expired)
    verify(crest).refresh("bobkey")
  }

  "CrestKeyGrader" should "mark users as unclassified if they had one good key" in {
    val crest = mock[CrestApi]
    val p = Pilot("bob", Pilot.Status.internal, "myalliance", "mycorp", "Bob", "bob@bob.com", Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")), List("group1", "group3"), List("1:badkey", "123:bobkey"), List.empty )
    val c = new CrestKeyGrader(crest)
    when(crest.refresh("bobkey")(global)).thenReturn(Future{new CallbackResponse("bobrefresh", null, 0, None)})
    when(crest.refresh("badkey")(global)).thenReturn(Future{throw new Exception("nope sorry")})
    when(crest.verify("bobrefresh")(global)).thenReturn(Future{new VerifyResponse(123, "Bob", null, null, null, null, null)})
    c.grade(p) must equal(Pilot.Status.unclassified)
    verify(crest).refresh("bobkey")
    verify(crest).verify("bobrefresh")
  }

  "CrestKeyGrader" should "mark users as expired if their keys are good but none are for their main" in {
    val crest = mock[CrestApi]
    val p = Pilot("bob", Pilot.Status.internal, "myalliance", "mycorp", "Bob", "bob@bob.com", Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")), List("group1", "group3"), List("1:bobakey", "2:bobbkey"), List.empty )
    val c = new CrestKeyGrader(crest)
    when(crest.refresh("bobakey")(global)).thenReturn(Future{new CallbackResponse("bobarefresh", null, 0, None)})
    when(crest.refresh("bobbkey")(global)).thenReturn(Future{new CallbackResponse("bobbrefresh", null, 0, None)})
    when(crest.verify("bobarefresh")(global)).thenReturn(Future{new VerifyResponse(1, "BobA", null, null, null, null, null)})
    when(crest.verify("bobbrefresh")(global)).thenReturn(Future{new VerifyResponse(2, "BobB", null, null, null, null, null)})
    c.grade(p) must equal(Pilot.Status.expired)
    verify(crest).refresh("bobakey")
    verify(crest).refresh("bobbkey")
    verify(crest).verify("bobarefresh")
    verify(crest).verify("bobbrefresh")
  }




}
