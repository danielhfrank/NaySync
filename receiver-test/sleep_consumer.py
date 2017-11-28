import json
import time

from consumer_base import ConsumerBase


class SleepConsumer(ConsumerBase):

    def logic(self, message_body):
        msg_dict = json.loads(message_body)
        duration = msg_dict.get('sleep_s', 0.0)
        time.sleep(duration)
        result = msg_dict['echo'].upper().replace('?', '')
        return result
