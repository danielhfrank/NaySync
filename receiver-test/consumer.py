import json
import base64

import nsq
import requests


def handler(message):
    msg_outer = json.loads(message.body)
    msg_body = base64.decodestring(msg_outer['bodyBase64'])
    print msg_outer
    print "Received message id %s, triggering callback" % msg_outer['requestId']
    response = requests.post('http://127.0.0.1:8080/complete', params={'id': msg_outer['requestId']}, data=msg_body.upper())
    print response
    return True


def main():
    r = nsq.Reader(message_handler=handler,
                   nsqd_tcp_addresses=['127.0.0.1:4150'],
                   topic='test-naysync', channel='asdf')
    nsq.run()

if __name__ == '__main__':
    main()
