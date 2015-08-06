import socket
import random
import json
from const import RECV_BUF_SIZE


def byteify(input):
    '''
    convert unicode from json.loads to utf-8
    '''
    if isinstance(input, dict):
        return {byteify(key): byteify(value) for key, value in input.iteritems()}
    elif isinstance(input, list):
        return [byteify(element) for element in input]
    elif isinstance(input, unicode):
        return input.encode('utf-8')
    else:
        return input


class LinkLog(object):
    def __init__(self, host_id, peer_lists):
        self.log = {}
        self.peer_num = len(peer_lists) - 1     # not self

    def update(self, switch, link, event, state, peer=None):
        if switch not in self.log:
            self.log[switch] = {}
        if link not in self.log[switch]:
            self.log[switch][link] = {}
        if event not in self.log[switch][link]:
            self.log[switch][link][event] = {}
            self.log[switch][link][event]['state'] = state
        if peer is not None:
            self.log[switch][link][event]['peers'].append(peer)

    def digest(self):
        '''
        calculate summary of log to be sent
        '''
        digest = {}
        for switch in self.log:
            digest[switch] = {}
            for link in self.log[switch]:
                digest[switch][link] = []
                last_event = None
                events = sorted(self.log[switch][link].keys())
                for e in events:
                    if last_event is None:
                        digest[switch][link].append(e)
                    elif e - last_event > 1:
                        digest[switch][link].append((last_event, e))
                    elif e == events[-1]:
                        digest[switch][link].append(e)
                    last_event = e
        return digest

    def subtract_events(self, switch, link, events):
        ret = []
        self_events = sorted(self.log[switch][link].keys())
        # TODO: refine code
        for e in events:
            if e is events[0]:
                for self_e in self_events:
                    if e > self_e:
                        ret.append((
                            self_e, self.log[switch][link][self_e]['state']))
            if e is events[-1]:
                for self_e in self_events:
                    if e < self_e:
                        ret.append((
                            self_e, self.log[switch][link][self_e]['state']))
            if e is not events[0] and e is not events[-1]:
                for self_e in self_events:
                    if e[0] < self_e and e[1] > self_e:
                        ret.append((
                            self_e, self.log[switch][link][self_e]['state']))
        return ret

    def subtract_log(self, digest):
        delta = {}
        for switch in self.log:
            if switch not in digest:
                delta[switch] = self.log[switch]
            else:
                delta[switch] = {}
                for link in self.log[switch]:
                    if link not in digest[switch]:
                        delta[switch][link] = self.log[switch][link]
                    else:
                        delta[switch][link] = self.subtract_events(
                                switch, link, digest[switch][link])
                        if not delta[switch][link]:
                            del delta[switch][link]
                if not delta[switch]:
                    del delta[switch]
        return delta

    def truncate(self):
        '''
        truncate tail, if all messages have beed seen by each others
        '''
        # TODO: how can host know that others have received its message?
        for switch in self.log:
            for link in self.log[switch]:
                events = sorted(self.log[switch][link].keys())
                for e in events:
                    if len(self.log[switch][link][e]['peers']) is self.peer_num:
                        del self.log[switch][link][e]   # or write it to disk
                    else:
                        break


class Gossiper(object):
    def __init__(self, host_id, peer_lists, timer, streams, logger):
        self._open(host_id, peer_lists)
        self.timer = timer
        # link_log type {sw: [max_version, {'link': [version, state]}]}
        self.link_log = streams.link_log
        self.logger = logger

    def _open(self, host_id, peer_lists):
        self.sock = socket.socket(
                socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self.sock.setblocking(0)            # non-blocking
        self.sock.bind(peer_lists[host_id])
        del peer_lists[host_id]
        self.peer_lists = peer_lists

    def _handle_msg(self, data, addr):
        '''
                 **pull method**
        |---       syn, digest        -->|
        |                                |
        |<-- ack, response of missing ---|
        '''
        if data['type'] == 'syn':
            self._handle_syn(data, addr)
        elif data['type'] == 'ack':
            self._handle_ack(data, addr)

    def _handle_syn(self, data, addr):
        self.logger.debug(
                "receive syn from %s, digest: %s" % (
                    addr[0], json.dumps(data['digest'])))

        delta = self.link_log.subtract_log(data['digest'])

        if delta:
            self.logger.debug(
                    "send ack to %s, delta: %s" % (
                        addr[0], json.dumps(delta)))
            self.sock.sendto(json.dumps(
                {'type': 'ack', 'delta': delta}), addr)

    def _handle_ack(self, data, addr):
        self.logger.debug(
                "receive ack from %s, delta: %s" % (
                    addr[0], json.dumps(data['delta'])))
        for switch in data['delta']:
            for link in data['delta'][switch]:
                for event, state in data['delta'][switch][link].iteritems():
                    self.link_log.update(switch, link, event, state, addr)

    def wait(self, selector):
        selector.wait([self.sock], [])

    def run(self, lists):
        # socket is readable
        if self.sock in lists[0]:
            self.logger.info("current link log: %s" % json.dumps(self.link_log))
            data, addr = self.sock.recvfrom(RECV_BUF_SIZE)
            self._handle_msg(byteify(json.loads(data)), addr)

        # check timer, time up per second
        if self.timer.time_up:
            addr = random.choice(self.peer_lists)
            self.logger.debug(
                    "send syn to %s, digest: %s" % (
                        addr[0], json.dumps(self.link_log.digest())))
            self.sock.sendto(json.dumps({
                'type': 'syn', 'digest': self.link_log.digest()
                }), addr)
