import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.io.Buf.{ByteArray, ByteBuffer}
import com.twitter.util.{Future, Promise, Return, Throw, Try => TwitterTry}

import com.twitter.bijection.Codec

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class NaySyncMux[ServiceResult: Codec](topic: String, publisher: QueuePublisher[_]) {

  val meatLocker = new ConcurrentHashMap[String, Promise[ServiceResult]]() asScala

  def mintId(req: Request): String = {
    val md5 = MessageDigest.getInstance("md5")
    md5.update(ByteBuffer.Shared.extract(req.content))
    md5.digest().map("%02X".format(_)).mkString
  }

  def readResult(buf: Buf): Try[ServiceResult] =
    implicitly[Codec[ServiceResult]].invert(ByteArray.Shared.extract(buf))

  val submitSvc = new Service[Request, Response] {
    override def apply(request: Request): Future[Response] = {
      val reqId = mintId(request)
      val pubResult = publisher.publish(topic, request.content)
      pubResult
        .transform{
          case Return(_) =>
            val completionPromise = new Promise[ServiceResult]()
            meatLocker.putIfAbsent(reqId, completionPromise)
            completionPromise
          case Throw(e) =>
            Future.exception[ServiceResult](e)
        }
        .map{ completionResult =>
          val res = Response(Status.Ok)
          val resultBytes = implicitly[Codec[ServiceResult]].apply(completionResult)
          res.content(ByteArray.Owned(resultBytes))
          res
        }
    }

    val completeSvc = new Service[Request, Response] {
      override def apply(request: Request): Future[Response] = {

        val maybeStoredPromise = request.params.get("id")
          .flatMap(meatLocker.get)

        val tryStoredPromise = maybeStoredPromise match {
          case Some(storedPromise) => Success(storedPromise)
          case None => Failure(new IllegalArgumentException("Request id not found"))
        }

        val tryDeliveredResult = readResult(request.content)

        val couldComplete = for {
          storedPromise <- tryStoredPromise
          deliveredResult <- tryDeliveredResult
        } yield {
          // this is it! set the promise with the value we got back
          storedPromise.setValue(deliveredResult)
        }
        val resp = couldComplete.map{ _ =>
          Response(Status.Ok)
        }
        Future.const[Response](TwitterTry.fromScala(resp))
      }
    }
  }

}
