import json
import base64

import requests


class ConsumerBase(object):

    def handler(self, message):
        msg_outer = json.loads(message.body)
        msg_body = base64.decodestring(msg_outer['bodyBase64'])

        result = self.logic(msg_body)
        response = requests.post('http://127.0.0.1:8080/complete',
                                 params={'id': msg_outer['requestId']}, data=result)
        print response
        return True

    def logic(self, message_body):
        raise NotImplementedError
