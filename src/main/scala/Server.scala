import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.http.codec.HttpCodec
import com.twitter.finagle.http.path.{ParamMatcher, Root}
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.{Http, Service, http}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.http.path._

object Server extends App {

  val CallbackHost = "http://127.0.0.1:8080" // could let user provide an accessible url via cmd line or something

  val pub = new NSQPublisher
  val mux = new NaySyncMux(CallbackHost, pub)

  val routingService = RoutingService.byPathObject[Request] {
    case Root / "submit" / topic => mux.submitSvc(topic)
    case Root / "complete" / reqId => mux.completeSvc(reqId)
    case Root / "retrieve" / reqId => mux.retrieveSvc(reqId)
  }
  val server = Http.serve(":8080", routingService)
  Await.ready(server)
}
