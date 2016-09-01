package moe.pizza.auth.webapp.rest

import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.Authorization
import org.http4s.util.Writer
import org.scalatest.{FlatSpec, MustMatchers}

/**
  * Created by andi on 06/07/16.
  */
class RestKeyMiddlewareSpec extends FlatSpec with MustMatchers {

  val emptyservice = HttpService {
    case req @ GET -> Root =>
      Ok("inner service")
  }

  val svc = new RestKeyMiddleware(List("key1", "key2")).apply(emptyservice)

  "when wrapping a service it" should "pass through calls with valid keys" in {
    val r = svc
      .apply(
        new Request(uri = Uri.uri("/"),
                    headers =
                      Headers(new Authorization(OAuth2BearerToken("key1")))))
      .run
    r.status must equal(Ok)
  }

  "when wrapping a service it" should "fail calls without valid keys" in {
    val r = svc
      .apply(
        new Request(uri = Uri.uri("/"),
                    headers =
                      Headers(new Authorization(OAuth2BearerToken("key3")))))
      .run
    r.status must equal(Unauthorized)
  }

}
