import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.http.codec.HttpCodec
import com.twitter.finagle.http.path.{ParamMatcher, Root}
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.{Http, Service, http}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.http.path._

object Server extends App {
  val service = new Service[Request, Response] {
    def apply(req: Request): Future[Response] = {
      val x = Response(req.version, Status.Ok)
      x.setContentString("yo")
      Future.value(
        x
      )
    }
  }

  def echoService(message: String) = new Service[Request, Response] {
    def apply(req: Request): Future[Response] = {
      val rep = Response(req.version, Status.Ok)
      rep.setContentString(message)

      Future(rep)
    }
  }

  val routingService = RoutingService.byPathObject[Request] {
    case Root => service
    case Root /  "echo" / message => echoService(message)
  }
  val server = Http.serve(":8080", routingService)
  Await.ready(server)
}
