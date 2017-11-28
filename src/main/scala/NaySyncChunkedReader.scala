import com.twitter.concurrent.AsyncStream
import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Future, Promise}

object NaySyncChunkedReader {

  def apply(requestId: String, resultPromise: Future[Buf]): Reader = {
    val reqIdReader: Reader = Reader.fromBuf(Buf.ByteArray.Owned(requestId.getBytes("utf-8")))
    val futureContentReader: Future[Reader] = resultPromise.map { result => Reader.fromBuf(result) }

    val resultStream: AsyncStream[Reader] = reqIdReader +:: AsyncStream.fromFuture(futureContentReader)

    Reader.concat(resultStream)
  }
}
