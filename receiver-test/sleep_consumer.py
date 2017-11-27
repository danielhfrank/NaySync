import json
import time

from consumer_base import ConsumerBase


class SleepConsumer(ConsumerBase):

    def logic(self, message_body):
        msg_dict = json.loads(message_body)
        duration = msg_dict['sleep_s']
        time.sleep(duration)
        return msg_dict['echo']
