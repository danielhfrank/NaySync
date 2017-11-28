
import com.twitter.bijection.codec.Base64
import com.twitter.io.Buf
import com.twitter.io.Buf.ByteArray

import io.circe._, io.circe.generic.semiauto._

case class Payload(requestId: String, callback: String, bodyBase64: String)

object Payload {

  def mk(requestId: String, callbackHost: String, body: Buf): Payload =
    Payload(
      requestId,
      s"$callbackHost/complete/$requestId",
      new Base64().encodeAsString(ByteArray.Shared.extract(body))
    )

  val encoder: ObjectEncoder[Payload] = deriveEncoder[Payload]

  def toBuf(payload: Payload): Buf = ByteArray.Shared.apply(encoder(payload).noSpaces.getBytes("utf-8"))
}