# NaySync
"It's not async!"

The year is 2017, and many of us are fully on [the log/queue bandwagon](https://engineering.linkedin.com/distributed-systems/log-what-every-software-engineer-should-know-about-real-time-datas-unifying). But, much of the world still wants to speak synchronous RPC - what to do?

It's been suggested that, in such a case, an API server could accept a request, enqueue it for later processing while still holding open the original request, and then return a response when the asynchronous processing completes - within a timeout, of course. NaySync is a server that does just this.

The screencast below probably best illustrates this:
![demo](img_todo)

On the right we are running a consumer process that runs our application code (you can find the code in `test_receiver/sleep_consumer.py`. On the left we make curl requests to the server.
1. In the first request, we submit a payload that is handled quickly and get back a response. We can see the consumer process it.
2. In the second request, we submit a payload that takes a long time to process, and exceeds the specified timeout. We can see the server return early with a request id that we can fetch later, and the consumer finish processing after that.
3. In the third request, we look up the request id returned in the previous response, and get the result

# Running locally

In one window: `nsqd`

In another window: `cd test-receiver; python consumer.py` (requires `requests`, `pynsq`)

In another window `sbt 'run-main Server'`
