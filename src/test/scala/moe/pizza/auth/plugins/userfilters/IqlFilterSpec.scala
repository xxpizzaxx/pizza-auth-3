package moe.pizza.auth.plugins.userfilters

import moe.pizza.auth.models.Pilot
import org.scalatest.{FlatSpec, MustMatchers}

/**
  * Created by Andi on 23/02/2016.
  */
class IqlFilterSpec extends FlatSpec with MustMatchers {

  "IqlFilter" should "filter users using IQL" in {
    val iqlf = new IqlFilter
    val p = Pilot("bob",
                  Pilot.Status.internal,
                  "myalliance",
                  "mycorp",
                  "Bob",
                  "bob@bob.com",
                  Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")),
                  List("group1", "group3"),
                  List.empty,
                  List.empty)
    val p2 = Pilot("terry",
                   Pilot.Status.internal,
                   "myalliance",
                   "mycorp",
                   "Terry",
                   "bob@bob.com",
                   Pilot.OM.readTree("{\"meta\": \"%s\"}".format("metafield")),
                   List("group1", "group2"),
                   List.empty,
                   List.empty)
    val input = List(p, p2)
    iqlf.filter(input, ".uid == \"bob\"") must equal(List(p))
    iqlf.filter(input, ".uid == \"terry\"") must equal(List(p2))
    iqlf.filter(input, "1 == 1") must equal(List(p, p2))
    iqlf.filter(
      input,
      "(.corporation == \"mycorp\") && (.alliance == \"myalliance\")") must equal(
      List(p, p2))
    iqlf.filter(
      input,
      "(.corporation == \"mycorp\") && (.metadata.meta == \"metafield\") && (.alliance == \"myalliance\")") must equal(
      List(p, p2))
    iqlf.filter(
      input,
      "(.corporation == \"mycorp\") && (.metadata.meta == \"metafiel\") && (.alliance == \"myalliance\")") must equal(
      List.empty[Pilot])
    iqlf.filter(
      input,
      "(.corporation == \"mycorp\") && (\"group1\" in .authGroups)") must equal(
      List(p, p2))
    iqlf.filter(
      input,
      "(.corporation == \"mycorp\") && (\"group2\" in .authGroups)") must equal(
      List(p2))
  }

}
