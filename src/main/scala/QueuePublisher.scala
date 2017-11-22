import com.twitter.io.Buf
import com.twitter.util.Future

trait QueuePublisher[Response] {

  def publish(topic: String, content: Buf): Future[Response]

}
