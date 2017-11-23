import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.io.Buf.ByteBuffer
import com.twitter.util.{Future, Promise, Return, Throw}

import scala.collection.JavaConverters._

class NaySyncMux(topic: String, publisher: QueuePublisher[_]) {

  val meatLocker = new ConcurrentHashMap[String, Promise[Buf]]() asScala

  def mintId(req: Request): String = {
    val md5 = MessageDigest.getInstance("md5")
    md5.update(ByteBuffer.Shared.extract(req.content))
    md5.digest().map("%02X".format(_)).mkString
  }

  val submitSvc = new Service[Request, Response] {
    override def apply(request: Request): Future[Response] = {
      val reqId = mintId(request)
      val payload = Payload.mk(reqId, request.content)
      val pubResult = publisher.publish(topic, Payload.toBuf(payload))
      pubResult
        .transform {
          case Return(_) => // TODO this is where it would be better if we could store e.g. the offset that we got from kafka
            val completionPromise = new Promise[Buf]()
            meatLocker.putIfAbsent(reqId, completionPromise)
            completionPromise
          case Throw(e) =>
            Future.exception[Buf](e)
        }
        .map { completionResult =>
          val res = Response(Status.Ok)
          res.content(completionResult)
          res
        }
    }
  }

  val completeSvc = new Service[Request, Response] {
    override def apply(request: Request): Future[Response] = {

      val maybeStoredPromise = request.params.get("id")
        .flatMap(meatLocker.remove)

      val resp = maybeStoredPromise match {

        case Some(storedPromise) =>
          // I am super-defensively copying shit around, might not need to
          val copiedBuf: Buf = ByteBuffer.Owned(ByteBuffer.Shared.extract(request.content))
          // this is it! set the promise with the value we got back
          storedPromise.setValue(copiedBuf)

          // Now return OK
          Response(Status.Ok)
        case None =>
          Response(Status.NotFound)
      }

      Future.value(resp)
    }
  }

}
