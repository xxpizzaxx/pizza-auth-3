package moe.pizza.auth.plugins.pilotgraders

import moe.pizza.auth.models.Pilot
import moe.pizza.auth.plugins.pilotgraders.AlliedPilotGrader.SavedContactList
import moe.pizza.eveapi.endpoints.Corp
import moe.pizza.eveapi.generated.corp.ContactList.{Row, Rowset}
import moe.pizza.eveapi.{ApiKey, EVEAPI, XMLApiResponse}
import org.joda.time.DateTime
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{MustMatchers, WordSpec}
import org.http4s.client.blaze.PooledHttp1Client

import scalaz.concurrent.Task
import scalaxb.{DataRecord, XMLStandardTypes}
import scalaz.concurrent.Task

/**
  * Created by Andi on 26/02/2016.
  */
class AlliedPilotGraderSpec
    extends WordSpec
    with MustMatchers
    with MockitoSugar
    with XMLStandardTypes {

    implicit val client = PooledHttp1Client() //TODO: no mocking?

  "AlliedPilotGrader" when {
    "pulling contacts" should {
      "cope with failure when reading a corp contact list" in {
        val now = DateTime.now()
        val eveapi = mock[EVEAPI]
        val corp = mock[Corp]
        when(eveapi.corp).thenReturn(corp)
        when(corp.ContactList()).thenReturn(
          Task {
            throw new Exception("oh no")
          }
        )
        implicit val apikey = new ApiKey(1, "hi")
        val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi), null)
        val r = apg.pullAllies()
        r must equal(None)
      }
      "read a corp contact list" in {
        val now = DateTime.now()
        val eveapi = mock[EVEAPI]
        val corp = mock[Corp]
        when(eveapi.corp).thenReturn(corp)
        when(corp.ContactList()).thenReturn(
          Task {
            new XMLApiResponse(
              now,
              now,
              Seq(
                new Rowset(
                  Seq(
                    new Row(
                      Map(
                        "@contactTypeID" -> DataRecord.apply(null, BigInt(2)),
                        "@contactName" -> DataRecord.apply(null, "Terry"),
                        "@standing" -> DataRecord.apply(null, BigDecimal(10))
                      )
                    )
                  ),
                  Map(
                    "@name" -> DataRecord.apply(null, "corporateContactList"))
                )
              )
            )
          }
        )
        implicit val apikey = new ApiKey(1, "hi")
        val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi), null)
        val r = apg.pullAllies()
        r must equal(
          Some(new SavedContactList(now, List(), List("Terry"), List())))
      }
      "read an alliance contact list" in {
        val now = DateTime.now()
        val eveapi = mock[EVEAPI]
        val corp = mock[Corp]
        when(eveapi.corp).thenReturn(corp)
        when(corp.ContactList()).thenReturn(
          Task {
            new XMLApiResponse(
              now,
              now,
              Seq(
                new Rowset(
                  Seq(
                    new Row(
                      Map(
                        "@contactTypeID" -> DataRecord.apply(null, BigInt(2)),
                        "@contactName" -> DataRecord.apply(null, "Terry"),
                        "@standing" -> DataRecord.apply(null, BigDecimal(10))
                      )
                    )
                  ),
                  Map("@name" -> DataRecord.apply(null, "allianceContactList"))
                )
              )
            )
          }
        )
        implicit val apikey = new ApiKey(1, "hi")
        val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi), null)
        val r = apg.pullAllies()
        r must equal(
          Some(new SavedContactList(now, List(), List("Terry"), List())))
      }
    }
    "merge the lists" in {
      val now = DateTime.now()
      val eveapi = mock[EVEAPI]
      val corp = mock[Corp]
      when(eveapi.corp).thenReturn(corp)
      when(corp.ContactList()).thenReturn(
        Task {
          new XMLApiResponse(
            now,
            now,
            Seq(
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(2)),
                      "@contactName" -> DataRecord.apply(null, "Terry"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "allianceContactList"))
              ),
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(2)),
                      "@contactName" -> DataRecord.apply(null, "Terry2"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "corporateContactList"))
              )
            )
          )
        }
      )
      implicit val apikey = new ApiKey(1, "hi")
      val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi), null)
      val r = apg.pullAllies()
      r must equal(
        Some(
          new SavedContactList(now, List(), List("Terry2", "Terry"), List())))
    }
    "parse people, corps and alliance into the correct lists" in {
      val now = DateTime.now()
      val eveapi = mock[EVEAPI]
      val corp = mock[Corp]
      when(eveapi.corp).thenReturn(corp)
      when(corp.ContactList()).thenReturn(
        Task {
          new XMLApiResponse(
            now,
            now,
            Seq(
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(1373)),
                      "@contactName" -> DataRecord.apply(null, "Terry"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  ),
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(1375)),
                      "@contactName" -> DataRecord.apply(null, "Terry2"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  ),
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null,
                                                           BigInt(16159)),
                      "@contactName" -> DataRecord
                        .apply(null, "Terry's Cool Alliance"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "allianceContactList"))
              ),
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(2)),
                      "@contactName" -> DataRecord.apply(null, "TerryCorp"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "corporateContactList"))
              )
            )
          )
        }
      )
      implicit val apikey = new ApiKey(1, "hi")
      val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi), null)
      val r = apg.pullAllies()
      r must equal(
        Some(
          new SavedContactList(now,
                               List("Terry2", "Terry"),
                               List("TerryCorp"),
                               List("Terry's Cool Alliance"))))
    }
    "obey the flags for which lists to parse" in {
      val now = DateTime.now()
      val eveapi = mock[EVEAPI]
      val corp = mock[Corp]
      when(eveapi.corp).thenReturn(corp)
      when(corp.ContactList()).thenReturn(
        Task {
          new XMLApiResponse(
            now,
            now,
            Seq(
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(1373)),
                      "@contactName" -> DataRecord.apply(null, "Terry"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  ),
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(1375)),
                      "@contactName" -> DataRecord.apply(null, "Terry2"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  ),
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null,
                                                           BigInt(16159)),
                      "@contactName" -> DataRecord
                        .apply(null, "Terry's Cool Alliance"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "allianceContactList"))
              ),
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(2)),
                      "@contactName" -> DataRecord.apply(null, "TerryCorp"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "corporateContactList"))
              )
            )
          )
        }
      )
      implicit val apikey = new ApiKey(1, "hi")
      val apg = new AlliedPilotGrader(5.0,
                                      true,
                                      usealliance = false,
                                      Some(eveapi),
                                      null)
      val r = apg.pullAllies()
      r must equal(
        Some(new SavedContactList(now, List(), List("TerryCorp"), List())))
    }
    "obey the threshold" in {
      val now = DateTime.now()
      val eveapi = mock[EVEAPI]
      val corp = mock[Corp]
      when(eveapi.corp).thenReturn(corp)
      when(corp.ContactList()).thenReturn(
        Task {
          new XMLApiResponse(
            now,
            now,
            Seq(
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(1373)),
                      "@contactName" -> DataRecord.apply(null, "Terry"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(2))
                    )
                  ),
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(1375)),
                      "@contactName" -> DataRecord.apply(null, "Terry2"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(7.2))
                    )
                  ),
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null,
                                                           BigInt(16159)),
                      "@contactName" -> DataRecord
                        .apply(null, "Terry's Cool Alliance"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "allianceContactList"))
              ),
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(2)),
                      "@contactName" -> DataRecord.apply(null, "TerryCorp"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(-10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "corporateContactList"))
              )
            )
          )
        }
      )
      implicit val apikey = new ApiKey(1, "hi")
      val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi), null)
      val r = apg.pullAllies()
      r must equal(
        Some(
          new SavedContactList(now,
                               List("Terry2"),
                               List(),
                               List("Terry's Cool Alliance"))))
    }
  }
  "grading pilots" should {
    "grade allied pilots as allies" in {
      val now = DateTime.now()
      val expiry = DateTime.now().plusHours(2)
      val eveapi = mock[EVEAPI]
      val corp = mock[Corp]
      when(eveapi.corp).thenReturn(corp)
      when(corp.ContactList()).thenReturn(
        Task {
          new XMLApiResponse(
            expiry,
            expiry,
            Seq(
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(1373)),
                      "@contactName" -> DataRecord.apply(null, "Terry"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  ),
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(1375)),
                      "@contactName" -> DataRecord.apply(null, "Terry2"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  ),
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null,
                                                           BigInt(16159)),
                      "@contactName" -> DataRecord
                        .apply(null, "Terry's Cool Alliance"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "allianceContactList"))
              ),
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(2)),
                      "@contactName" -> DataRecord.apply(null, "TerryCorp"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "corporateContactList"))
              )
            )
          )
        }
      )
      implicit val apikey = new ApiKey(1, "hi")
      val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi), null)
      val bob = new Pilot("bob",
                          Pilot.Status.unclassified,
                          "boballiance",
                          "bobcorp",
                          "Bob",
                          "none@none",
                          Pilot.OM.createObjectNode(),
                          List.empty[String],
                          List("1:REF"),
                          List.empty[String])
      apg.grade(bob) must equal(Pilot.Status.unclassified)
      apg.grade(bob.copy(characterName = "Terry")) must equal(
        Pilot.Status.ally)
      apg.grade(bob.copy(corporation = "TerryCorp")) must equal(
        Pilot.Status.ally)
      apg.grade(bob.copy(alliance = "Terry's Cool Alliance")) must equal(
        Pilot.Status.ally)
      verify(corp, times(1)).ContactList()
    }
    "pull a new contact list if the old one expired" in {
      val now = DateTime.now()
      val expiry = DateTime.now().plusHours(2)
      val expired = DateTime.now().minusHours(2)
      val eveapi = mock[EVEAPI]
      val corp = mock[Corp]
      when(eveapi.corp).thenReturn(corp)
      when(corp.ContactList()).thenReturn(
        Task {
          new XMLApiResponse(
            expired,
            expired,
            Seq(
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(1373)),
                      "@contactName" -> DataRecord.apply(null, "Terry"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  ),
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(1375)),
                      "@contactName" -> DataRecord.apply(null, "Terry2"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  ),
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null,
                                                           BigInt(16159)),
                      "@contactName" -> DataRecord
                        .apply(null, "Terry's Cool Alliance"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "allianceContactList"))
              ),
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(2)),
                      "@contactName" -> DataRecord.apply(null, "TerryCorp"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "corporateContactList"))
              )
            )
          )
        }
      )
      implicit val apikey = new ApiKey(1, "hi")
      val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi), null)
      when(corp.ContactList()).thenReturn(
        Task {
          new XMLApiResponse(
            expiry,
            expiry,
            Seq(
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(1373)),
                      "@contactName" -> DataRecord.apply(null, "Terry"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  ),
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(1375)),
                      "@contactName" -> DataRecord.apply(null, "Terry2"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  ),
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null,
                                                           BigInt(16159)),
                      "@contactName" -> DataRecord
                        .apply(null, "Terry's Cool Alliance"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "allianceContactList"))
              ),
              new Rowset(
                Seq(
                  new Row(
                    Map(
                      "@contactTypeID" -> DataRecord.apply(null, BigInt(2)),
                      "@contactName" -> DataRecord.apply(null, "TerryCorp"),
                      "@standing" -> DataRecord.apply(null, BigDecimal(10))
                    )
                  )
                ),
                Map("@name" -> DataRecord.apply(null, "corporateContactList"))
              )
            )
          )
        }
      )
      val bob = new Pilot("bob",
                          Pilot.Status.unclassified,
                          "boballiance",
                          "bobcorp",
                          "Bob",
                          "none@none",
                          Pilot.OM.createObjectNode(),
                          List.empty[String],
                          List("1:REF"),
                          List.empty[String])
      apg.grade(bob) must equal(Pilot.Status.unclassified)
      verify(corp, times(2)).ContactList()
    }
  }
  "cope with failure when there's no contact list" in {
    val now = DateTime.now()
    val eveapi = mock[EVEAPI]
    val corp = mock[Corp]
    when(eveapi.corp).thenReturn(corp)
    when(corp.ContactList()).thenReturn(
      Task {
        throw new Exception("oh no")
      }
    )
    implicit val apikey = new ApiKey(1, "hi")
    val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi), null)
    val r = apg.pullAllies()
    r must equal(None)
    val bob = new Pilot("bob",
                        Pilot.Status.unclassified,
                        "boballiance",
                        "bobcorp",
                        "Bob",
                        "none@none",
                        Pilot.OM.createObjectNode(),
                        List.empty[String],
                        List("1:REF"),
                        List.empty[String])
    apg.grade(bob) must equal(Pilot.Status.unclassified)
    verify(corp, times(2)).ContactList()
  }
  "classify with old data if required" in {
    val now = DateTime.now()
    val expiry = DateTime.now().plusHours(2)
    val expired = DateTime.now().minusHours(2)
    val eveapi = mock[EVEAPI]
    val corp = mock[Corp]
    when(eveapi.corp).thenReturn(corp)
    when(corp.ContactList()).thenReturn(
      Task {
        new XMLApiResponse(
          expired,
          expired,
          Seq(
            new Rowset(
              Seq(
                new Row(
                  Map(
                    "@contactTypeID" -> DataRecord.apply(null, BigInt(1373)),
                    "@contactName" -> DataRecord.apply(null, "Terry"),
                    "@standing" -> DataRecord.apply(null, BigDecimal(10))
                  )
                ),
                new Row(
                  Map(
                    "@contactTypeID" -> DataRecord.apply(null, BigInt(1375)),
                    "@contactName" -> DataRecord.apply(null, "Terry2"),
                    "@standing" -> DataRecord.apply(null, BigDecimal(10))
                  )
                ),
                new Row(
                  Map(
                    "@contactTypeID" -> DataRecord.apply(null, BigInt(16159)),
                    "@contactName" -> DataRecord
                      .apply(null, "Terry's Cool Alliance"),
                    "@standing" -> DataRecord.apply(null, BigDecimal(10))
                  )
                )
              ),
              Map("@name" -> DataRecord.apply(null, "allianceContactList"))
            ),
            new Rowset(
              Seq(
                new Row(
                  Map(
                    "@contactTypeID" -> DataRecord.apply(null, BigInt(2)),
                    "@contactName" -> DataRecord.apply(null, "TerryCorp"),
                    "@standing" -> DataRecord.apply(null, BigDecimal(10))
                  )
                )
              ),
              Map("@name" -> DataRecord.apply(null, "corporateContactList"))
            )
          )
        )
      }
    )
    implicit val apikey = new ApiKey(1, "hi")
    val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi), null)
    when(corp.ContactList()).thenReturn(
      Task {
        throw new Exception("oh no")
      }
    )
    val bob = new Pilot("bob",
                        Pilot.Status.unclassified,
                        "boballiance",
                        "bobcorp",
                        "Bob",
                        "none@none",
                        Pilot.OM.createObjectNode(),
                        List.empty[String],
                        List("1:REF"),
                        List.empty[String])
    apg.grade(bob) must equal(Pilot.Status.unclassified)
    apg.grade(bob.copy(characterName = "Terry")) must equal(Pilot.Status.ally)
    apg.grade(bob.copy(corporation = "TerryCorp")) must equal(
      Pilot.Status.ally)
    apg.grade(bob.copy(alliance = "Terry's Cool Alliance")) must equal(
      Pilot.Status.ally)
    verify(corp, times(5)).ContactList()
  }
}
