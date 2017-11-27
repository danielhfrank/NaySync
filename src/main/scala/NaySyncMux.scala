import java.security.MessageDigest
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.io.{Buf, Reader}
import com.twitter.io.Buf.ByteBuffer
import com.twitter.util._

import scala.collection.JavaConverters._

class NaySyncMux(topic: String, publisher: QueuePublisher[_]) {

  implicit val timer = new JavaTimer()

  val meatLocker = new ConcurrentHashMap[String, Promise[Buf]]().asScala

  // Putting this in separate map to simulate using external storage (would not want to pin lost requests to single host)
  val lostAndFound = new ConcurrentHashMap[String, Buf]().asScala

  /*
   TODO for next pass:
   - stick a `within` on the end of our promise. catch it expiring and return a message to the user with the request id
   - (maybe) make a fetch endpoint that takes a request id
   - in the complete endpoint, if req id is not present in the map, don't return error to user. Instead, fill the meat locker
     with a filled promise for the computed result
   - similarly, in the submit endpoint, if a value is already present, take on that future instead of creating a new promise
      (it will presumably be the filled one from a late arrival)

      OPEN QUESTIONS - do all these enhancements miss the point by doing this in a non-distributed context? Would it be much
      trouble to annex all of that to a secondary map so that we can separate out the parts that would have to be put in real storage?
   */

  def mintId(req: Request): String = {
    val md5 = MessageDigest.getInstance("md5")
    md5.update(ByteBuffer.Shared.extract(req.content))
    md5.digest().map("%02X".format(_)).mkString
  }

  val submitSvc = new Service[Request, Response] {
    override def apply(request: Request): Future[Response] = {
      val completionPromise = new Promise[Buf]()

      val reqId = mintId(request)
      val payload = Payload.mk(reqId, request.content)
      val pubResult = publisher.publish(topic, Payload.toBuf(payload))

      pubResult
        .transform {
          case Return(_) => // TODO this is where it would be better if we could store e.g. the offset that we got from kafka
            meatLocker.putIfAbsent(reqId, completionPromise)
            completionPromise.within(Duration(1, TimeUnit.SECONDS))
          case Throw(e) =>
            Future.exception[Buf](e)
        }
        .map { completionResult =>
          val res = Response(Status.Ok)
          res.content(completionResult)
          res
        }
        .rescue{
          case _: TimeoutException =>
            // We blew through the timeout above; return something nice to the user
            // But first, remove ourselves from the meat locker
            meatLocker.remove(reqId, completionPromise)
            val res = NaySyncMux.responseFromString(request, Status.ServiceUnavailable, s"Exceeded timeout, request id is $reqId")
            Future.value(res)
        }
    }
  }

  def completeSvc(reqId: String) = new Service[Request, Response] {
    override def apply(request: Request): Future[Response] = {

      val maybeStoredPromise = meatLocker.remove(reqId)
      val resp = maybeStoredPromise match {
        case Some(storedPromise) => // we found a pending request in the meatlocker, complete it with the result

          // I am super-defensively copying shit around, might not need to
          val copiedBuf: Buf = ByteBuffer.Owned(ByteBuffer.Shared.extract(request.content))
          // this is it! set the promise with the value we got back
          storedPromise.setValue(copiedBuf)

          // Now return OK
          Response(Status.Ok)

        case None => // we did NOT find a pending request in the meatlocker. Put it in lostAndFound instead
          // (this might mean that we had blown through our timeout, but could also mean that, say, the request had already been completed)
          // TODO opportunity for a leak here, put some kind of cleanup on lostAndFound?
          val prevLostAndFound = lostAndFound.putIfAbsent(reqId, request.content)
          prevLostAndFound match {
            case Some(prevVal) => NaySyncMux.responseFromBuf(request, Status.Conflict, prevVal)
            case None => Response(Status.Processing)
          }
      }
      Future.value(resp)
    }
  }

  def retrieveSvc(reqId: String) = new Service[Request, Response] {
    override def apply(request: Request): Future[Response] = {
      Future.value{
        lostAndFound.get(reqId)
          .map{ storedVal =>
            NaySyncMux.responseFromBuf(request, Status.Ok, storedVal)
          }
          .getOrElse(Response(Status.NotFound))
      }
    }
  }


}

object NaySyncMux {
  private def responseFromString(request: Request, status: Status, content: String): Response = {
    val buf = Buf.ByteArray.Owned.apply(content.getBytes("utf-8"))
    responseFromBuf(request, status, buf)
  }

  private def responseFromBuf(request: Request, status: Status, buf: Buf): Response = {
    Response(request.version, status, Reader.fromBuf(buf))
  }
}
