package moe.pizza.auth.plugins

import moe.pizza.eveapi.generated.corp.ContactList.{Row, Rowset}
import moe.pizza.eveapi.{ApiKey, XMLApiResponse, EVEAPI}
import moe.pizza.eveapi.endpoints.Corp
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatest.{WordSpec, MustMatchers}
import org.mockito.Mockito.{when, verify, never, reset, times, spy}
import org.mockito.Matchers.{anyString, anyInt}
import moe.pizza.auth.plugins.AlliedPilotGrader.SavedContactList

import scala.concurrent.Future
import scala.util.Try
import scala.xml.Elem
import scalaxb.{XMLStandardTypes, DataRecord}

import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Created by Andi on 26/02/2016.
  */
class AlliedPilotGraderSpec extends WordSpec with MustMatchers with MockitoSugar with XMLStandardTypes {


  "AlliedPilotGrader" when {
    "pulling contacts" should {
      "read a corp contact list" in {
        val now = DateTime.now()
        val eveapi = mock[EVEAPI]
        val corp = mock[Corp]
        when(eveapi.corp).thenReturn(corp)
        when(corp.ContactList()).thenReturn(
          Future {
            Try {
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
                    Map("@name" -> DataRecord.apply(null, "corporateContactList"))
                  )
                )
              )
            }
          }
        )
        implicit val apikey = new ApiKey(1, "hi")
        val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi))
        val r = apg.pullAllies()
        r must equal(Some(new SavedContactList(now, List(), List("Terry"), List())))
      }
      "read an alliance contact list" in {
        val now = DateTime.now()
        val eveapi = mock[EVEAPI]
        val corp = mock[Corp]
        when(eveapi.corp).thenReturn(corp)
        when(corp.ContactList()).thenReturn(
          Future {
            Try {
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
          }
        )
        implicit val apikey = new ApiKey(1, "hi")
        val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi))
        val r = apg.pullAllies()
        r must equal(Some(new SavedContactList(now, List(), List("Terry"), List())))
      }
    }
    "merge the lists" in {
      val now = DateTime.now()
      val eveapi = mock[EVEAPI]
      val corp = mock[Corp]
      when(eveapi.corp).thenReturn(corp)
      when(corp.ContactList()).thenReturn(
        Future {
          Try {
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
        }
      )
      implicit val apikey = new ApiKey(1, "hi")
      val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi))
      val r = apg.pullAllies()
      r must equal(Some(new SavedContactList(now, List(), List("Terry2", "Terry"), List())))
    }
    "parse people, corps and alliance into the correct lists" in {
      val now = DateTime.now()
      val eveapi = mock[EVEAPI]
      val corp = mock[Corp]
      when(eveapi.corp).thenReturn(corp)
      when(corp.ContactList()).thenReturn(
        Future {
          Try {
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
                        "@contactTypeID" -> DataRecord.apply(null, BigInt(16159)),
                        "@contactName" -> DataRecord.apply(null, "Terry's Cool Alliance"),
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
        }
      )
      implicit val apikey = new ApiKey(1, "hi")
      val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi))
      val r = apg.pullAllies()
      r must equal(Some(new SavedContactList(now, List("Terry2", "Terry"),List("TerryCorp"),List("Terry's Cool Alliance"))))
    }
    "obey the flags for which lists to parse" in {
      val now = DateTime.now()
      val eveapi = mock[EVEAPI]
      val corp = mock[Corp]
      when(eveapi.corp).thenReturn(corp)
      when(corp.ContactList()).thenReturn(
        Future {
          Try {
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
                        "@contactTypeID" -> DataRecord.apply(null, BigInt(16159)),
                        "@contactName" -> DataRecord.apply(null, "Terry's Cool Alliance"),
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
        }
      )
      implicit val apikey = new ApiKey(1, "hi")
      val apg = new AlliedPilotGrader(5.0, true, usealliance=false, Some(eveapi))
      val r = apg.pullAllies()
      r must equal(Some(new SavedContactList(now, List(),List("TerryCorp"),List())))
    }
    "obey the threshold" in {
      val now = DateTime.now()
      val eveapi = mock[EVEAPI]
      val corp = mock[Corp]
      when(eveapi.corp).thenReturn(corp)
      when(corp.ContactList()).thenReturn(
        Future {
          Try {
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
                        "@contactTypeID" -> DataRecord.apply(null, BigInt(16159)),
                        "@contactName" -> DataRecord.apply(null, "Terry's Cool Alliance"),
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
        }
      )
      implicit val apikey = new ApiKey(1, "hi")
      val apg = new AlliedPilotGrader(5.0, true, true, Some(eveapi))
      val r = apg.pullAllies()
      r must equal(Some(new SavedContactList(now, List("Terry2"),List(),List("Terry's Cool Alliance"))))
    }
  }

}
