package moe.pizza.auth.webapp.rest

import moe.pizza.auth.config.ConfigFile.ConfigFile
import moe.pizza.auth.graphdb.EveMapDb
import moe.pizza.auth.interfaces.{PilotGrader, UserDatabase, BroadcastService}
import BroadcastService._
import moe.pizza.auth.tasks.Update
import moe.pizza.crestapi.CrestApi
import org.http4s.{HttpService, _}
import org.http4s.dsl.{Root, _}
import org.http4s.server._
import org.http4s.server.staticcontent.ResourceService
import org.http4s.server.syntax.ServiceOps
import org.joda.time.DateTime
import play.twirl.api.Html
import moe.pizza.eveapi._
import scala.concurrent.ExecutionContext.Implicits.global
import org.http4s.twirl._
import scala.concurrent.Future
import scala.util.Try
import scalaz._
import Scalaz._
import scala.util.{Success => TSuccess}
import scala.util.{Failure => TFailure}
import scala.concurrent.duration._
import scala.concurrent.Await
import moe.pizza.crestapi.character.location.Types.Location
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import io.circe.parse._
import io.circe.syntax._
import org.http4s.circe._
import io.circe.Json

class RestResource(fullconfig: ConfigFile,
                   graders: PilotGrader,
                   portnumber: Int = 9021,
                   ud: UserDatabase,
                   crestapi: Option[CrestApi] = None,
                   eve: Option[EVEAPI] = None,
                   mapper: Option[EveMapDb] = None,
                   updater: Option[Update] = None,
                   broadcasters: List[BroadcastService] = List.empty[BroadcastService]
                  ) {

  case class ApiError(`type`: String, message: String)

  case class PingRequest(message: String, from: String, to: String)
  case class PingResponse(total: Int)

  def resource = HttpService {

    case req@GET -> Root / "ping" / "group" / group => {
      req.decode[Json] { p =>
        p.as[PingRequest].toOption.map { pingreq =>
          val users = ud.getUsers(s"authgroup=${group}")
          val templatedMessage = templates.txt.broadcast(pingreq.message, pingreq.to, pingreq.from, DateTime.now())
          val sendreqs = ud.sendGroupAnnouncement(broadcasters, templatedMessage.toString(), pingreq.from, users )
          val r = Await.result(Future.sequence(sendreqs), 2 seconds).sum
          Ok(PingResponse(r).asJson)
        }.getOrElse {
          BadRequest(ApiError("bad_post_body", "Unable to process your post body, please format it correctly").asJson)
        }
      }
    }

  }


}
