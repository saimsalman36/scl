import json
import networkx as nx
import pox.openflow.libopenflow_01 as of
from collections import defaultdict
from pox.core import core
from pox.lib.util import dpid_to_str
from pox.lib.addresses import IPAddr

log = core.getLogger()

# NOTE: hard code, assume the switch name is sXXX type
def dpid2name(dpid):
    return 's' + str(dpid).zfill(3)


def byteify(input): # Understood -> Ported
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


class Link(object): # Understood -> Ported
    def __init__(
            self, sw1, intf1, sw2, intf2, port1=None, port2=None,
            state1=of.OFPPS_LINK_DOWN, state2=of.OFPPS_LINK_DOWN):
        self.sw1 = sw1
        self.intf1 = intf1
        self.port1 = port1
        self.state1 = state1
        self.sw2 = sw2
        self.intf2 = intf2
        self.port2 = port2
        self.state2 = state2
        # default host link state is up
        if self.sw1[0] == 'h':
            self.state1 = of.OFPPS_STP_FORWARD
            self.port1 = 1
        if self.sw2[0] == 'h':
            self.state2 = of.OFPPS_STP_FORWARD
            self.port2 = 1


class scl_routing(object):
    '''
    proactive mode: update routing table according to link states
    '''
    def __init__(self, name): # Understood -> Ported
        log.debug("SAIM: Initialize SCL_Routing POX Module")
        self.topo = None
        self.graph = nx.Graph()
        self.hosts = {}         # [host] --> host_ip
        self.sw2conn = {}       # [sw] --> connection
        # [intf] --> link_obj
        self.intf2link = defaultdict(lambda: None)
        # [sw1][sw2] --> link_obj
        self.sw2link = defaultdict(lambda: defaultdict(lambda: None))
        # [sw][host1][host2] --> link_obj
        self.sw_tables = defaultdict(
                lambda: defaultdict(lambda: defaultdict(lambda: None)))
        # [sw][host1][host2] --> update_status
        self.sw_tables_status = defaultdict(
                lambda: defaultdict(lambda: defaultdict(lambda: None)))
        self.load_topo(name)
        core.openflow.addListeners(self)

    def load_topo(self, name): # Understood -> Ported
        with open(name) as in_file:
            self.topo = byteify(json.load(in_file))
            
            # self.topo contains:

            # {'switches': {'s018': '10.0.18.1', 's019': '10.0.19.1', 's012': '10.0.12.1', 's013': '10.0.13.1', 's010': '10.0.10.1', 's011': '10.0.11.1', 's016': '10.0.16.1', 's017': '10.0.17.1', 's014': '10.0.14.1', 's015': '10.0.15.1', 's005': '10.0.5.1', 's004': '10.0.4.1', 's007': '10.0.7.1', 's006': '10.0.6.1', 's001': '10.0.1.1', 's000': '10.0.0.1', 's003': '10.0.3.1', 's002': '10.0.2.1', 's009': '10.0.9.1', 's008': '10.0.8.1'}, 
            # 'hosts': {'h014': '10.1.14.1', 'h008': '10.1.8.1', 'h009': '10.1.9.1', 'h004': '10.1.4.1', 'h005': '10.1.5.1', 'h006': '10.1.6.1', 'h007': '10.1.7.1', 'h000': '10.1.0.1', 'h001': '10.1.1.1', 'h002': '10.1.2.1', 'h003': '10.1.3.1', 'h011': '10.1.11.1', 'h012': '10.1.12.1', 'h013': '10.1.13.1', 'h010': '10.1.10.1', 'h015': '10.1.15.1'}, 
            # 'links': [['s011', 's011-eth0', 1, 's002', 's002-eth3', 4], ['s005', 's005-eth3', 4, 's013', 's013-eth1', 2], ['s007', 's007-eth1', 2, 's003', 's003-eth1', 2], ['s009', 's009-eth0', 1, 's002', 's002-eth2', 3], ['s006', 's006-eth0', 1, 's000', 's000-eth1', 2], ['s010', 's010-eth0', 1, 's000', 's000-eth3', 4], ['s017', 's017-eth2', 3, 'h010', 'h010-eth0', 1], ['s011', 's011-eth3', 4, 's019', 's019-eth1', 2], ['s005', 's005-eth1', 2, 's003', 's003-eth0', 1], ['s007', 's007-eth3', 4, 's015', 's015-eth1', 2], ['s011', 's011-eth1', 2, 's003', 's003-eth3', 4], ['s008', 's008-eth2', 3, 's016', 's016-eth0', 1], ['s008', 's008-eth3', 4, 's017', 's017-eth0', 1], ['s008', 's008-eth0', 1, 's000', 's000-eth2', 3], ['s010', 's010-eth1', 2, 's001', 's001-eth3', 4], ['s004', 's004-eth0', 1, 's000', 's000-eth0', 1], ['s015', 's015-eth2', 3, 'h006', 'h006-eth0', 1], ['s010', 's010-eth3', 4, 's019', 's019-eth0', 1], ['s009', 's009-eth3', 4, 's017', 's017-eth1', 2], ['s014', 's014-eth3', 4, 'h005', 'h005-eth0', 1], ['s006', 's006-eth3', 4, 's015', 's015-eth0', 1], ['s005', 's005-eth2', 3, 's012', 's012-eth1', 2], ['s005', 's005-eth0', 1, 's002', 's002-eth0', 1], ['s014', 's014-eth2', 3, 'h004', 'h004-eth0', 1], ['s011', 's011-eth2', 3, 's018', 's018-eth1', 2], ['s013', 's013-eth2', 3, 'h002', 'h002-eth0', 1], ['s004', 's004-eth2', 3, 's012', 's012-eth0', 1], ['s016', 's016-eth3', 4, 'h009', 'h009-eth0', 1], ['s018', 's018-eth3', 4, 'h013', 'h013-eth0', 1], ['s008', 's008-eth1', 2, 's001', 's001-eth2', 3], ['s006', 's006-eth2', 3, 's014', 's014-eth0', 1], ['s009', 's009-eth1', 2, 's003', 's003-eth2', 3], ['s019', 's019-eth2', 3, 'h014', 'h014-eth0', 1], ['s004', 's004-eth1', 2, 's001', 's001-eth0', 1], ['s006', 's006-eth1', 2, 's001', 's001-eth1', 2], ['s010', 's010-eth2', 3, 's018', 's018-eth0', 1], ['s009', 's009-eth2', 3, 's016', 's016-eth1', 2], ['s012', 's012-eth2', 3, 'h000', 'h000-eth0', 1], ['s012', 's012-eth3', 4, 'h001', 'h001-eth0', 1], ['s018', 's018-eth2', 3, 'h012', 'h012-eth0', 1], ['s007', 's007-eth0', 1, 's002', 's002-eth1', 2], ['s004', 's004-eth3', 4, 's013', 's013-eth0', 1], ['s007', 's007-eth2', 3, 's014', 's014-eth1', 2], ['s017', 's017-eth3', 4, 'h011', 'h011-eth0', 1]], 
            # 'ctrls': ['h003', 'h007', 'h008', 'h015']}


            if self.topo:
                for host in self.topo['hosts'].keys():
                    if 'ctrls' in self.topo and host in self.topo['ctrls']:
                        continue
                    self.graph.add_node(host)
                    self.hosts[host] = self.topo['hosts'][host]
                log.debug('total edge num: %d' % len(self.topo['links']))
                for l in self.topo['links']:
                    if len(self.topo['links'][0]) == 6:
                        sw1, intf1, port1, sw2, intf2, port2 = \
                                l[0], l[1], l[2], l[3], l[4], l[5]
                        # sw1 or sw2 may also contain a host but not the controllers.
                        link_obj = Link(sw1, intf1, sw2, intf2, port1, port2)
                    elif len(self.topo['links'][0]) == 4:
                        log.debug("SAIM: Does the code ever go here!")
                        sw1, intf1, sw2, intf2 = l[0], l[1], l[2], l[3]
                        link_obj = Link(sw1, intf1, sw2, intf2)
                    self.sw2link[sw1][sw2] = link_obj
                    self.intf2link[sw1 + ':' + intf1] = link_obj
                    self.sw2link[sw2][sw1] = link_obj
                    self.intf2link[sw2 + ':' + intf2] = link_obj

            # log.debug("This isn't possible!")
            # log.debug(self.sw_tables)
            # log.debug(self.sw_tables_status)

    def graph_add_edge(self, sw1, sw2): # Understood
        self.graph.add_edge(sw1, sw2, weight=1)

    def _handle_ConnectionUp(self, event): # Understood
        log.debug("Switch %s up.", dpid_to_str(event.dpid))
        sw_name = dpid2name(event.dpid)
        log.debug("SWITCH-NAME: " + sw_name)
        #if sw_name not in self.topo['switches']:
        #    log.error('sw: %s not in topology' % sw_name)
        #    return
        if self.graph.has_node(sw_name):
            log.error('sw: %s is in current graph' % sw_name)
            return
        self.graph.add_node(sw_name)
        self.sw2conn[sw_name] = event.connection

    def _handle_ConnectionDown(self, event): # Understood
        log.debug("Switch %s down.", dpid_to_str(event.dpid))
        sw_name = dpid2name(event.dpid)
        if not self.graph.has_node(sw_name):
            log.error('sw: %s is not in current graph' % sw_name)
            return
        self.graph.remove_node(sw_name)
        del self.sw2conn[sw_name]
        for sw2 in self.sw2link[sw_name]:
            link = self.sw2link[sw_name][sw2]
            if sw_name is link.sw1:
                link.state1 = of.OFPPS_LINK_DOWN
            else:
                link.state2 = of.OFPPS_LINK_DOWN
        self.update_flow_tables(self._calculate_shortest_route())

    def _handle_PortStatus(self, event): # Understood!
        log.debug("CALLED!");

        assert event.modified is True

        # Sample Event: {'added': False, 'halt': False, 'deleted': False, 'modified': True, 'port': 3, 
        # 'source': <pox.openflow.OpenFlowNexus object at 0x7f9c929c4ad0>, 'connection': <pox.openflow.of_01.Connection object at 0x7f9c9008f410>, 
        # 'dpid': 12, 'ofp': <pox.openflow.libopenflow_01.ofp_port_status object at 0x7f9c9009da90>}

        # event.ofp.desc Example:  {'hw_addr': EthAddr('00:00:00:00:00:00'), 'curr': 0, 
        # 'name': 's011-eth2', 'supported': 0, 'state': 0, 'advertised': 0, 'peer': 0, 'config': 0, 'port_no': 3}

        log.debug("Switch %s portstatus upcall.", dpid_to_str(event.dpid))
        log.debug("     port: %s, state: %d" % (event.ofp.desc.name, event.ofp.desc.state)) # port: s012-eth2, state: 0
        link = self.intf2link[dpid2name(event.dpid) + ':' + event.ofp.desc.name]

        # Link Example: {'intf1': 's004-eth0', 'intf2': 's000-eth0', 'state2': 1, 'state1': 1, 'sw1': 's004', 'sw2': 's000', 'port2': 1, 'port1': 1}

        if link is None:
            # The links switches have with controllers are None in the intf2link dict.
            return
        if link.sw1 == dpid2name(event.dpid):
            link.port1 = event.ofp.desc.port_no
        else:
            link.port2 = event.ofp.desc.port_no
        if not link:
            log.debug('control <---> data link intf')
            return
        sw1, sw2 = link.sw1, link.sw2
        old_state = link.state1 if dpid2name(event.dpid) == sw1 else link.state2
        if event.ofp.desc.state != of.OFPPS_LINK_DOWN:
            # we do not distinguish stp state types
            event.ofp.desc.state = of.OFPPS_STP_FORWARD
        if event.ofp.desc.state == old_state:
            log.debug("sw %s intf %s state is already %d, sw1: %s, sw2: %s, intf1: %s, intf2: %s, state1: %s, state2: %s" % (dpid2name(event.dpid), event.ofp.desc.name, old_state, sw1, sw2, link.intf1, link.intf2, link.state1, link.state2))
            return

        # All the code below is literally the same.
        # Wait for both links to be up.
        # Then Add Edge in Graph.
        # Update Flow Tables.
        if event.ofp.desc.state != of.OFPPS_LINK_DOWN:
            if dpid2name(event.dpid) == sw1:
                link.state1 = event.ofp.desc.state
                if link.state2 != of.OFPPS_LINK_DOWN:
                    # both ends of the link are up, update route
                    log.debug("both ends of the link are up, add edge %s %s" % (sw1, sw2))
                    self.graph_add_edge(sw1, sw2)
                    self.update_flow_tables(self._calculate_shortest_route())
                else:
                    log.debug('one end of the link is up, wait for the other end')
                    return
            else:
                link.state2 = event.ofp.desc.state
                if link.state1 != of.OFPPS_LINK_DOWN:
                    # both ends of the link are up, update route
                    log.debug("both ends of the link are up, add edge %s %s" % (sw1, sw2))
                    self.graph_add_edge(sw1, sw2)
                    self.update_flow_tables(self._calculate_shortest_route())
                else:
                    log.debug('one end of the link is up, wait for the other end')
                    return
        else:
            if dpid2name(event.dpid) == sw1:
                link.state1 = event.ofp.desc.state
                if link.state2 != of.OFPPS_LINK_DOWN:
                    # an end of the link is down, update route
                    log.debug("one end of the link is down, remove edge %s %s" % (sw1, sw2))
                    self.graph.remove_edge(sw1, sw2)
                    self.update_flow_tables(self._calculate_shortest_route())
                else:
                    log.debug('both ends of the link are down')
                    return
            else:
                link.state2 = event.ofp.desc.state
                if link.state1 != of.OFPPS_LINK_DOWN:
                    # an end of the link is down, update route
                    log.debug("one end of the link is down, remove edge %s %s" % (sw1, sw2))
                    self.graph.remove_edge(sw1, sw2)
                    self.update_flow_tables(self._calculate_shortest_route())
                else:
                    log.debug('both ends of the link are down')
                    return

    def _calculate_shortest_route(self): # Understood
        # log.debug("calculate shortest path routing...")
        # log.debug("Before: ------------------------------------------")
        # log.debug(self.sw_tables)
        # log.debug(self.sw_tables_status)
        # log.debug("Before: ------------------------------------------")
        log.debug("edges, num %d: %s", len(self.graph.edges()), json.dumps(self.graph.edges()))
        updates = {'modify': defaultdict(lambda: []), 'delete' : defaultdict(lambda: [])}
        current = 0
        for host1 in self.hosts:
            for host2 in self.hosts:
                if host1 is host2:
                    continue
                try:
                    paths = list(nx.all_shortest_paths(self.graph, host1, host2, 'weight'))
                except nx.exception.NetworkXNoPath:
                    continue
                path = paths[current % len(paths)]
                # put hostpaths on all path candidates equally
                current += 1
                log.debug('calculated path: %s' % json.dumps(path))
                path = zip(path, path[1:]) # Saim: Genious but simplistic!
                for (a, b) in path[1:]:
                    link = self.sw2link[a][b]
                    if self.sw_tables[a][host1][host2] != link:
                        self.sw_tables[a][host1][host2] = link
                        updates['modify'][a].append((host1, host2, link))
                        self.sw_tables_status[a][host1][host2] = 'updated'
                    else:
                        self.sw_tables_status[a][host1][host2] = 'checked'
        for sw in self.sw_tables_status.keys():
            for host1 in self.sw_tables_status[sw].keys():
                for host2 in self.sw_tables_status[sw][host1].keys():
                    if self.sw_tables_status[sw][host1][host2] is not 'updated' and\
                       self.sw_tables_status[sw][host1][host2] is not 'checked':
                        updates['delete'][sw].append((
                            host1, host2, self.sw_tables[sw][host1][host2]))
                        del self.sw_tables[sw][host1][host2]
                        del self.sw_tables_status[sw][host1][host2]
                    else:
                        # log.debug("SAIM: Does the code ever come here?")
                        # log.debug(self.sw_tables_status[sw][host1][host2])
                        self.sw_tables_status[sw][host1][host2] = 'to_be_deleted'
        # log.debug("After: ------------------------------------------")
        # log.debug(self.sw_tables)
        # log.debug(self.sw_tables_status)
        # log.debug("SAIM: " + updates)
        # log.debug("After: ------------------------------------------")
        return updates

    def update_flow_entry(self, sw_name, host1, host2, link, cmd): # Understood
        nw_src = self.hosts[host1]
        nw_dst = self.hosts[host2]
        # types of sw_name and link.sw1 are different, use == not is
        if sw_name == link.sw1:
            #log.debug(
            #        'host1 %s --> host2 %s; link: %s %s %d --> %s %s %d' % (
            #            nw_src, nw_dst, link.sw1, link.intf1, link.port1, link.sw2, link.intf2, link.port2))
            outport = link.port1
        else:
            #log.debug(
            #        'host1 %s --> host2 %s; link: %s %s %d --> %s %s %d' % (
            #            nw_src, nw_dst, link.sw2, link.intf2, link.port2, link.sw1, link.intf1, link.port1))
            outport = link.port2
        msg = of.ofp_flow_mod(command = cmd)
        msg.match.dl_type = 0x800
        msg.match.nw_src = IPAddr(nw_src)
        msg.match.nw_dst = IPAddr(nw_dst)
        msg.priority = 50000    # hard code
        msg.actions.append(of.ofp_action_output(port = outport))
        if sw_name in self.sw2conn:
        	# log.debug("SAIM - P Start")
        	# log.debug(msg.pack())
        	# log.debug("SAIM - P End")
            self.sw2conn[sw_name].send(msg.pack())

    def update_flow_tables(self, updates): # Understood
        if not updates['modify'] and not updates['delete']:
            return
        log.debug("update flow tables")
        total, modify, delete  = 0, 0, 0

        for sw_name, flow_entries in updates['modify'].iteritems():
            modify = 0
            for host1, host2, link in flow_entries:
                modify += 1
                self.update_flow_entry(sw_name, host1, host2, link, of.OFPFC_MODIFY)
            log.debug('modify sw flow_entries: %s, size %d' % (sw_name, modify))
            total += modify

        for sw_name, flow_entries in updates['delete'].iteritems():
            delete = 0
            for host1, host2, link in flow_entries:
                delete += 1
                self.update_flow_entry(sw_name, host1, host2, link, of.OFPFC_DELETE)
            log.debug('delete sw flow_entries: %s, size %d' % (sw_name, delete))
            total += delete

        log.debug('total changed flow_entries size %d' % total)


def launch(name=None):
    if not name:
        log.info('input topology configuration file first')
        return
    # SAIM -- Pox Command: cd .. && python pox/pox.py log.level --DEBUG 
    # log --format="%(asctime)s - %(name)s - %(levelname)s - %(message)s" --datefmt="%Y%m%d %H:%M:%S" 
    # scl_routing --name=/home/saimsalman/scl/conf/fattree_inband.json > log/ctrl_0.log 2>&1 &
    core.registerNew(scl_routing, name)
