import com.twitter.concurrent.{AsyncMutex, AsyncStream}
import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Future, Promise}

class NaySyncChunkedReader(requestId: String, resultPromise: Future[Buf]) extends Reader {
  private val bytes = requestId.getBytes("utf-8")
  val reqIdBuf = Buf.ByteArray.Owned(bytes)
  val reqIdReader: Reader = Reader.fromBuf(reqIdBuf)
  val futureContentReader: Future[Reader] = resultPromise.map { result => Reader.fromBuf(result) }

  private[this] val mutex = new AsyncMutex()
  @volatile private[this] var idHasBeenRead = false


  override def read(n: Int): Future[Option[Buf]] = {
    mutex.acquireAndRun{
      if(!idHasBeenRead){
        println(s"Serving the read for the request id with n=$n out of ${bytes.size}")
        idHasBeenRead = true // TODO gnar around if not entire thing is read??
        reqIdReader.read(n)
      } else {
        futureContentReader.flatMap(_.read(n))
      }
    }
  }

  override def discard(): Unit = {
    reqIdReader.discard()
    futureContentReader.foreach(_.discard())
  }
}

object NaySyncChunkedReader {

  def apply(requestId: String, resultPromise: Future[Buf]): Reader = {
    val reqIdReader: Reader = Reader.fromBuf(Buf.ByteArray.Owned(requestId.getBytes("utf-8")))
    val futureContentReader: Future[Reader] = resultPromise.map { result => Reader.fromBuf(result) }

    val resultStream: AsyncStream[Reader] = reqIdReader +:: AsyncStream.fromFuture(futureContentReader)

    Reader.concat(resultStream)
  }
}
