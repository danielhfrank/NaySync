import json
import base64

import requests


class ConsumerBase(object):

    def handler(self, message):
        msg_outer = json.loads(message.body)
        req_id = msg_outer['requestId']
        msg_body = base64.decodestring(msg_outer['bodyBase64'])
        print 'Processing message %s with body "%s"' % (req_id, msg_body)
        result = self.logic(msg_body)
        response = requests.post(msg_outer['callback'],
                                 data=result)
        print 'Completed callback with status %d' % response.status_code
        return True

    def logic(self, message_body):
        raise NotImplementedError
