import com.twitter.finagle.Http
import com.twitter.finagle.http.{Request, RequestBuilder}
import com.twitter.io.Buf
import com.twitter.io.Buf.ByteArray
import com.twitter.util.{Await, Future}
class NSQPublisher() extends QueuePublisher[String] {

  val nsqdService = Http.newService(s"inet!${NSQPublisher.DEFAULT_URL}")

  override def publish(topic: String, content: Buf): Future[String] = {
    val req: Request = RequestBuilder()
      .url(s"http://${NSQPublisher.DEFAULT_URL}/${NSQPublisher.PUB_PATH}?topic=$topic")
      .buildPost(content)

    nsqdService(req)
      .map(_.getContentString()) // always just returns OK
  }
}

object NSQPublisher{
  val DEFAULT_URL = "127.0.0.1:4151"
  val PUB_PATH = "pub"

  def main(args: Array[String]): Unit = {
    val pub = new NSQPublisher()
    val content = "asdf".getBytes("utf-8")
    val resFtr = pub.publish("test", ByteArray.Owned(content))
    val res = Await.result(resFtr)
    println(res)
  }
}
