package moe.pizza.auth.webapp

import moe.pizza.auth.interfaces.UserDatabase
import org.http4s._
import org.http4s.dsl._
import org.http4s.util.CaseInsensitiveString
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}
import moe.pizza.auth.webapp.Utils._

class SessionManagerSpec extends FlatSpec with MustMatchers with MockitoSugar {

  val emptyservice = HttpService {
    case req @ GET -> Root =>
      Ok(req.getSession.toString)
    case req @ GET -> Root / "flash" =>
      val newsession = req.flash(Alerts.info, "this is an alert")
      Ok(req.getSession.toString).attachSessionifDefined(newsession)
    case req @ GET -> Root / "logout" =>
      Ok(req.getSession.toString).clearSession()
  }

  val ud = mock[UserDatabase]

  val svc = new SessionManager("keygoeshere", ud).apply(emptyservice)

  "when wrapping a service it" should "add a session cookie" in {
    val r = svc.apply(new Request(uri = Uri.uri("/"))).run
    r.status must equal(Ok)
    val session = r.headers.get(CaseInsensitiveString("set-cookie")).get
    session.value.startsWith("authsession=") must equal(true)
    val bodytxt = EntityDecoder.decodeString(r)(Charset.`UTF-8`).run
    bodytxt must equal("Some(HydratedSession(List(),None,None))")
  }

  "when wrapping a service it" should "add a session cookie and use it to store state between calls" in {
    val r = svc.apply(new Request(uri = Uri.uri("/flash"))).run
    r.status must equal(Ok)
    val session = r.headers.get(CaseInsensitiveString("set-cookie")).get
    session.value.startsWith("authsession=") must equal(true)
    val cookie = session.value
    val r2 = svc
      .apply(
        new Request(uri = Uri.uri("/"),
                    headers = Headers(Header("Cookie", cookie))))
      .run
    r.status must equal(Ok)
    val bodytxt = EntityDecoder.decodeString(r2)(Charset.`UTF-8`).run
    bodytxt must equal(
      "Some(HydratedSession(List(Alert(info,this is an alert)),None,None))")
  }

  "when wrapping a service it" should "be able to remove sessions" in {
    val r = svc.apply(new Request(uri = Uri.uri("/logout"))).run
    r.status must equal(Ok)
    val removal = r.headers.get(CaseInsensitiveString("set-cookie")).get
    assert(removal.value.startsWith("authsession=\"\";"))
  }

}
