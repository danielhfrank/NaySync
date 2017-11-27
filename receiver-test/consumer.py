import nsq

from sleep_consumer import SleepConsumer


def main():
    consumer = SleepConsumer()
    r = nsq.Reader(message_handler=consumer.handler,
                   nsqd_tcp_addresses=['127.0.0.1:4150'],
                   topic='test-naysync', channel='asdf')
    nsq.run()

if __name__ == '__main__':
    main()
