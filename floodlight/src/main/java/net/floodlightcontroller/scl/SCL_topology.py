#!/usr/bin/python

"""
A simple minimal topology script for Mininet.

Based in part on examples in the [Introduction to Mininet] page on the Mininet's
project wiki.

[Introduction to Mininet]: https://github.com/mininet/mininet/wiki/Introduction-to-Mininet#apilevels

"""

from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.net import Mininet
from mininet.topo import Topo
from mininet.node import RemoteController, OVSSwitch
from mininet.link import TCLink
import time
# from topo import topo2file, FatTree, FatTreeOutBand

class MinimalTopo( Topo ):
    "Minimal topology with a single switch and two hosts"

    def build( self ):

        h000 = self.addHost("h000", ip='10.1.0.1');
        h001 = self.addHost("h001", ip='10.1.1.1');
        h002 = self.addHost("h002", ip='10.1.2.1');
        h003 = self.addHost("h003", ip='10.1.3.1');
        h004 = self.addHost("h004", ip='10.1.4.1');
        h005 = self.addHost("h005", ip='10.1.5.1');
        h006 = self.addHost("h006", ip='10.1.6.1');
        h007 = self.addHost("h007", ip='10.1.7.1');
        h008 = self.addHost("h008", ip='10.1.8.1');
        h009 = self.addHost("h009", ip='10.1.9.1');
        h010 = self.addHost("h010", ip='10.1.10.1');
        h011 = self.addHost("h011", ip='10.1.11.1');
        h012 = self.addHost("h012", ip='10.1.12.1');
        h013 = self.addHost("h013", ip='10.1.13.1');
        h014 = self.addHost("h014", ip='10.1.14.1');
        h015 = self.addHost("h015", ip='10.1.15.1');

        # s021 = self.addSwitch("s021", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:21", ip='10.0.21.1');
        s020 = self.addSwitch("s000", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:14", ip='10.0.0.1');
        s001 = self.addSwitch("s001", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:01", ip='10.0.1.1');
        s002 = self.addSwitch("s002", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:02", ip='10.0.2.1');
        s003 = self.addSwitch("s003", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:03", ip='10.0.3.1');
        s004 = self.addSwitch("s004", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:04", ip='10.0.4.1');
        s005 = self.addSwitch("s005", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:05", ip='10.0.5.1');
        s006 = self.addSwitch("s006", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:06", ip='10.0.6.1');
        s007 = self.addSwitch("s007", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:07", ip='10.0.7.1');
        s008 = self.addSwitch("s008", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:08", ip='10.0.8.1');
        s009 = self.addSwitch("s009", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:09", ip='10.0.9.1');
        s010 = self.addSwitch("s010", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:0a", ip='10.0.10.1');
        s011 = self.addSwitch("s011", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:0b", ip='10.0.11.1');
        s012 = self.addSwitch("s012", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:0c", ip='10.0.12.1');
        s013 = self.addSwitch("s013", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:0d", ip='10.0.13.1');
        s014 = self.addSwitch("s014", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:0e", ip='10.0.14.1');       
        s015 = self.addSwitch("s015", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:0f", ip='10.0.15.1');
        s016 = self.addSwitch("s016", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:10", ip='10.0.16.1');
        s017 = self.addSwitch("s017", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:11", ip='10.0.17.1');
        s018 = self.addSwitch("s018", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:12", ip='10.0.18.1');
        s019 = self.addSwitch("s019", protocols='OpenFlow10', dpid="00:00:00:00:00:00:00:13", ip='10.0.19.1');

        self.addLink(s004, s020);
        self.addLink(s006, s020);
        self.addLink(s008, s020);
        self.addLink(s010, s020);

        self.addLink(s004, s001);
        self.addLink(s006, s001);
        self.addLink(s008, s001);
        self.addLink(s010, s001);

        self.addLink(s005, s002);
        self.addLink(s007, s002);
        self.addLink(s009, s002);
        self.addLink(s011, s002);

        self.addLink(s005, s003);
        self.addLink(s007, s003);
        self.addLink(s009, s003);
        self.addLink(s011, s003); 

        self.addLink(s012, s004);
        self.addLink(s013, s004);
        self.addLink(s012, s005);
        self.addLink(s013, s005);
        self.addLink(s014, s006);
        self.addLink(s015, s006);
        self.addLink(s014, s007);
        self.addLink(s015, s007);
        self.addLink(s016, s008);
        self.addLink(s017, s008);
        self.addLink(s016, s009);
        self.addLink(s017, s009);
        self.addLink(s018, s010);
        self.addLink(s019, s010);
        self.addLink(s018, s011);
        self.addLink(s019, s011);       

        self.addLink(h000, s012);
        self.addLink(h001, s012);
        self.addLink(h002, s013);
        self.addLink(h003, s013);
        self.addLink(h004, s014);
        self.addLink(h005, s014);
        self.addLink(h006, s015);
        self.addLink(h007, s015);
        self.addLink(h008, s016);
        self.addLink(h009, s016);
        self.addLink(h010, s017);
        self.addLink(h011, s017);
        self.addLink(h012, s018);
        self.addLink(h013, s018);
        self.addLink(h014, s019);
        self.addLink(h015, s019);
 

def runMinimalTopo():
    "Bootstrap a Mininet network using the Minimal Topology"

    # Create an instance of our topology
    topo = MinimalTopo()

    # Create a network based on the topology using OVS and controlled by
    # a remote controller.
    net = Mininet(
        topo=topo,
        controller=lambda name: RemoteController( name, ip='127.0.0.1', port=6633, protocols='OpenFlow10' ),
        switch=OVSSwitch,
        link=TCLink,
        autoSetMacs=True,
        autoStaticArp=True )

    # Actually start the network
    net.start()
    # time.sleep(5)
    # net.cmd(pingall(1)
    # Drop the user in to a CLI so user can run commands.
    CLI( net )
    # time.sleep(5)

    # After the user exits the CLI, shutdown the network.
    net.stop()

if __name__ == '__main__':
    # This runs if this file is executed directly
    setLogLevel( 'info' )
    runMinimalTopo()

# Allows the file to be imported using `mn --custom <filename> --topo minimal`
topos = {
    'minimal': MinimalTopo
}
