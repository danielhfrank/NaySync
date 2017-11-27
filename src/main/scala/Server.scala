import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.http.codec.HttpCodec
import com.twitter.finagle.http.path.{ParamMatcher, Root}
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.{Http, Service, http}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.http.path._

object Server extends App {

  val pub = new NSQPublisher
  val mux = new NaySyncMux("test-naysync", pub)

  val routingService = RoutingService.byPathObject[Request] {
    case Root / "submit" => mux.submitSvc
    case Root / "complete" / reqId => mux.completeSvc(reqId)
    case Root / "retrieve" / reqId => mux.retrieveSvc(reqId)
  }
  val server = Http.serve(":8080", routingService)
  Await.ready(server)
}
