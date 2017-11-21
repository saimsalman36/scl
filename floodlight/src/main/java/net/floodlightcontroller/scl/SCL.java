package net.floodlightcontroller.scl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;

import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;

// import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
// import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowModify;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructions;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.OFMessageDamper;
import org.projectfloodlight.openflow.protocol.OFType;
import java.util.EnumSet;

import java.util.Random;

class Triples {
    String host1;
    String host2;
    Link lnk;

    public Triples(String host1, String host2, Link lnk) {
        this.host1 = host1;
        this.host2 = host2;
        this.lnk = lnk;
    }

    public String toString() {
        return "Host-1: " + this.host1 + ", Host-2: " + this.host2 + ", LINK: " + this.lnk.toString();
    }
}

class NetworkX {
    Map<String, List<String>> graph;
    Map<String, Map<String, Integer>> dist;
    Map<String, Map<String, String>> next;

    public NetworkX() {
        graph = new HashMap<String, List<String>>();
        dist = new HashMap<String, Map<String, Integer>>();
        next = new HashMap<String, Map<String, String>>();
    }

    public String printGraph() {
        String ret = "";
        
        for (Map.Entry<String, List<String>> entry : this.graph.entrySet()) {
            ret += (entry.getKey() + ": ");

            List<String> temp = entry.getValue();

            if (temp != null) {
                for (Iterator<String> it = temp.iterator(); it.hasNext();) {
                    ret += (it.next() + ", ");
                }
                ret += '\n';
            }
        }

        return ret;
    }

    public List<String> listNodes() {
        List<String> ret = new ArrayList<String>(graph.keySet());
        ret.sort(String::compareToIgnoreCase);
        return ret;
    }

    public boolean addNode(String name) {
        if (hasNode(name) == false) {
            List<String> temp = new ArrayList<String>();
            this.graph.put(name, temp);
            return true;
        }

        return false;
    }

    public boolean removeNode(String name) {
        if (hasNode(name) == true) {
            List<String> copy = new ArrayList<String>(this.graph.get(name));
            for (int i = 0; i < copy.size(); i++) {
                removeEdge(name, copy.get(i));
            }   
            this.graph.remove(name);
            return true;
        }

        return false;
    }

    public boolean hasEdge(String node1, String node2) {
        if (hasNode(node1) == false || hasNode(node2) == false) {
            return false;
        }

        return this.graph.get(node1).contains(node2) && this.graph.get(node2).contains(node1);
    }

    public boolean addEdge(String node1, String node2) {
        if (hasNode(node1) == false || hasNode(node2) == false) {
            return false;
        }

        if (hasEdge(node1, node2) != true) {
            this.graph.get(node1).add(node2);
            this.graph.get(node2).add(node1);
            return true;
        }

        return false;
    }

    public boolean removeEdge(String node1, String node2) {
        if (hasEdge(node1, node2) == true) {
            this.graph.get(node1).remove(node2);
            this.graph.get(node2).remove(node1);
            return true;
        }

        return false;
    }

    public boolean hasNode(String name) {
        if (this.graph.size() == 0) {
            return false;
        }
        
        for (Map.Entry<String, List<String>> entry : this.graph.entrySet()) {
            if (entry.getKey().equals(name)) {
                return true;
            }
        }

        return false;
    }

    public void initializeParameters() {
        List<String> otherHosts = this.listNodes();
        otherHosts.forEach(i -> {
            otherHosts.forEach(j -> {
                if (this.dist.get(i) == null) {
                    Map<String, Integer> temp = new HashMap<String, Integer>();
                    temp.put(j, 9999);
                    this.dist.put(i, temp);

                    Map<String, String> tempNext = new HashMap<String, String>();
                    tempNext.put(j, "");
                    this.next.put(i, tempNext);
                } else {
                    this.dist.get(i).put(j, 9999);
                    this.next.get(i).put(j, "");
                }
            });
        });

        otherHosts.forEach(i -> {
            this.graph.get(i).forEach(j -> {
                this.dist.get(i).put(j, 1);
                this.next.get(i).put(j, j);
            });
        });
    }

    public void FloydWarshallWithPathReconstruction() {
        initializeParameters();

        List<String> otherHosts = listNodes();
        // System.out.println("Hosts Order: " + otherHosts);
        Integer counter = 1;

        for (String k: otherHosts) {
            for (String i: otherHosts) {
                for (String j: otherHosts) {
                    if (i.equals(j) || i.equals(k) || j.equals(k)) {
                        continue;
                    }

                    if (this.dist.get(i).get(j) == (this.dist.get(i).get(k) + this.dist.get(k).get(j))) {
                        if (counter == 1) {
                            this.dist.get(i).put(j, this.dist.get(i).get(k) + this.dist.get(k).get(j));
                            this.next.get(i).put(j, this.next.get(i).get(k));
                            counter = 0;
                        } else {
                            counter = counter + 1;
                        }
                    }

                    if (this.dist.get(i).get(j) > (this.dist.get(i).get(k) + this.dist.get(k).get(j))) {
                        this.dist.get(i).put(j, this.dist.get(i).get(k) + this.dist.get(k).get(j));
                        this.next.get(i).put(j, this.next.get(i).get(k));
                    }
                }
            }
        }
    }

    public List<String> Path(String node1, String node2) {
        if (this.next.get(node1) == null) return null;
        if (this.next.get(node1).get(node2) == "") return null;

        List<String> ret = new ArrayList<String>();
        ret.add(node1);

        while (!node1.equals(node2)) {
            node1 = this.next.get(node1).get(node2);
            if (node1 == null) return null;
            ret.add(node1);
        }

        return ret;
    }   

    public List<List<String>> printAllPaths(String node1, String node2) {
        Map<String, Boolean> visited = new HashMap<String, Boolean>();

        for (Map.Entry<String, List<String>> entry : this.graph.entrySet()) {
            visited.put(entry.getKey(), false);
        }

        List<String> path = new ArrayList<String>();
        List<List<String>> res = new ArrayList<List<String>>();
        printAllPaths_(node1, node2, visited, path, res);

        if (res.size() == 0) return res;

        Collections.sort(res,new Comparator<List<String>>() {
            @Override
            public int compare(List<String> lhs, List<String> rhs) {
                if (lhs.size() < rhs.size()) {
                    return -1;
                } else if (lhs.size() > rhs.size()) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        Integer minSizedPath = res.get(0).size();
        List<List<String>> temp = new ArrayList<List<String>>();

        for (int i = 0; i < res.size(); i++) {
            if (res.get(i).size() == minSizedPath) {
                temp.add(res.get(i));
            }
        }

        Collections.sort(temp,new Comparator<List<String>>() {
            @Override
            public int compare(List<String> lhs, List<String> rhs) {
                for (int i = 0; i < lhs.size(); i++) {
                    if (lhs.get(i).compareTo(rhs.get(i)) == 0) {
                        continue;
                    } else {
                        return (lhs.get(i).compareTo(rhs.get(i)));
                    }
                }

                return 0;
            }
        });

        return temp;
    }

    private void printAllPaths_(String u, String d, Map<String, Boolean> visited, List<String> path, List<List<String>> res) {
        visited.put(u, true);
        path.add(u);

        if (u.equals(d)) {
            List<String> newList = new ArrayList<String>();

            for (String str : path) {
                newList.add(str);   
            }

            res.add(newList);
        } else {
            List<String> temp = this.graph.get(u);

            if (temp != null) {
                for (int i = 0; i < temp.size(); i++) {
                    if (visited.get(temp.get(i)) == false) {
                        printAllPaths_(temp.get(i), d, visited, path, res);
                    }
                }
            }
        }

        path.remove(path.size() - 1);
        visited.put(u, false);
    }
}

class Link {
    String sw1;
    String intf1;
    int port1;
    int state1;
    String sw2;
    String intf2;
    int port2;
    int state2; //TODO: Change State from an Integer.

    public Link(String sw1, String intf1, String sw2, String intf2, int port1, int port2) {
        this.sw1 = sw1;
        this.intf1 = intf1;
        this.sw2 = sw2;
        this.intf2 = intf2;
        this.port1 = port1;
        this.port2 = port2;
        this.state1 = 1;
        this.state2 = 1;

        if (this.sw1.charAt(0) == 'h') {
            this.state1 = 512; // represents OFPPS_STP_FORWARD [POX].
            this.port1 = 1; // Because it's a host hence port 1.
        }

        if (this.sw2.charAt(0) == 'h') {
            this.state2 = 512; // represents OFPPS_STP_FORWARD [POX].
            this.port2 = 1; // Because it's a host hence port 1.
        }
    }

    public Link(String sw1, String intf1, String sw2, String intf2) {
        this.sw1 = sw1;
        this.intf1 = intf1;
        this.sw2 = sw2;
        this.intf2 = intf2; 
        this.port1 = -1;
        this.port2 = -1;
        this.state1 = 1; // 1 represents link down. of.OFPPS_LINK_DOWN in POX.
        this.state2 = 1; // 1 represents link down. of.OFPPS_LINK_DOWN in POX.
    }

    public String toString() {
        return "Switch SW1: " + sw1 + ", Interface-1: " + intf1 + ", Switch SW2: " + sw2 + ", Interface-2: " + intf2 + ", Port-1: " + port1 + ", Port-2: " + port2 + ", State-1: " + state1 + ", State-2: " + state2;
    }
}
 
public class SCL implements IFloodlightModule, IOFSwitchListener {
    protected static Map<String, IPv4Address> switches;
    protected static List<String> ctrls;
    protected static List<Link> links;
    protected static Map<String, Map<String, Map<String, Link>>> sw_tables;
    protected static Map<String, Map<String, Map<String, String>>> sw_tables_status;
    protected static Map<String, IPv4Address> hosts;
    protected static Map<String, Link> intf2link;
    protected static Map<String, Map<String, Link>> sw2link;
    protected static NetworkX networkGraph;
    protected static Map<String, IOFSwitch> swToConn;

    // protected ILinkDiscoveryService linkService;
    protected IOFSwitchService switchService;
    protected static Logger logger;
    protected final Object mutex = new Object();
    protected boolean isStarted;
    protected static List<Integer> EventCount;


    public void loadTopology(String jsonData) throws IOException{
        JsonParser jsonParser = new JsonFactory().createParser(jsonData);

        jsonParser.nextToken();
        if (jsonParser.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT");
        }

        while(jsonParser.nextToken() != JsonToken.END_OBJECT) {
            if (jsonParser.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("Expected FIELD_NAME");
            }

            String name = jsonParser.getCurrentName();

            if ("switches".equals(name)) {
                jsonParser.nextToken();
        
                if (jsonParser.getCurrentToken() != JsonToken.START_OBJECT) {
                    throw new IOException("Expected START_OBJECT");
                }

                while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                    jsonParser.nextToken();
                    this.switches.put(jsonParser.getCurrentName(), IPv4Address.of(jsonParser.getText()));
                }
            } else if ("hosts".equals(name)) {
                jsonParser.nextToken();
        
                if (jsonParser.getCurrentToken() != JsonToken.START_OBJECT) {
                    throw new IOException("Expected START_OBJECT");
                }

                while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                    jsonParser.nextToken();
                    this.hosts.put(jsonParser.getCurrentName(), IPv4Address.of(jsonParser.getText()));
                }
            } else if ("ctrls".equals(name)) {
                jsonParser.nextToken();

                if (jsonParser.getCurrentToken() != JsonToken.START_ARRAY) {
                    throw new IOException("Expected START_ARRAY");
                }

                while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
                    this.ctrls.add(jsonParser.getText());
                }
            } else if ("links".equals(name)) {
                jsonParser.nextToken();

                if (jsonParser.getCurrentToken() != JsonToken.START_ARRAY) {
                    throw new IOException("Expected START_ARRAY");
                }

                while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
                    if (jsonParser.getCurrentToken() != JsonToken.START_ARRAY) {
                        throw new IOException("Expected START_ARRAY");
                    }

                    int counter = 0;
                    ArrayList<String> tempStrings = new ArrayList<String>();

                    while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
                        tempStrings.add(jsonParser.getText());
                        counter = counter + 1;
                    }
                    Link tempLink = null;

                    if (counter == 4) {
                        // SW1, INTF1, SW2, INTF2
                        tempLink = new Link(tempStrings.get(0), tempStrings.get(1), tempStrings.get(2), tempStrings.get(3));

                        this.links.add(tempLink);
                        this.intf2link.put(tempStrings.get(0) + ":" + tempStrings.get(1),tempLink);
                        this.intf2link.put(tempStrings.get(2) + ":" + tempStrings.get(3),tempLink);

                        if (this.sw2link.get(tempStrings.get(0)) == null) {
                            Map<String, Link> temp = new HashMap<String, Link>();
                            temp.put(tempStrings.get(2), tempLink);
                            this.sw2link.put(tempStrings.get(0), temp);
                        } else {
                            this.sw2link.get(tempStrings.get(0)).put(tempStrings.get(2), tempLink);
                        }

                        if (this.sw2link.get(tempStrings.get(2)) == null) {
                            Map<String, Link> temp = new HashMap<String, Link>();
                            temp.put(tempStrings.get(0), tempLink);
                            this.sw2link.put(tempStrings.get(2), temp);
                        } else {
                            this.sw2link.get(tempStrings.get(2)).put(tempStrings.get(0), tempLink);
                        }

                    } else if (counter == 6) {
                        // SW1, INTF1, PORT1, SW2, INTF2, PORT2
                        tempLink = new Link(tempStrings.get(0), tempStrings.get(1), tempStrings.get(3), tempStrings.get(4), Integer.valueOf(tempStrings.get(2)), Integer.valueOf(tempStrings.get(5)));
                        
                        this.links.add(tempLink);
                        this.intf2link.put(tempStrings.get(0) + ":" + tempStrings.get(1),tempLink);
                        this.intf2link.put(tempStrings.get(3) + ":" + tempStrings.get(4),tempLink);

                        if (this.sw2link.get(tempStrings.get(0)) == null) {
                            Map<String, Link> temp = new HashMap<String, Link>();
                            temp.put(tempStrings.get(3), tempLink);
                            this.sw2link.put(tempStrings.get(0), temp);
                        } else {
                            this.sw2link.get(tempStrings.get(0)).put(tempStrings.get(3), tempLink);
                        }

                        if (this.sw2link.get(tempStrings.get(3)) == null) {
                            Map<String, Link> temp = new HashMap<String, Link>();
                            temp.put(tempStrings.get(0), tempLink);
                            this.sw2link.put(tempStrings.get(3), temp);
                        } else {
                            this.sw2link.get(tempStrings.get(3)).put(tempStrings.get(0), tempLink);
                        }
                    }
                }
            }   
        }

        for (String name: this.hosts.keySet()) {
            if (!this.ctrls.contains(name)) {
                // logger.info("Host Added: " + name);
                this.networkGraph.addNode(name);
            }
        }
    }
 
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }
 
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }
 
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
            new ArrayList<Class<? extends IFloodlightService>>();
        // l.add(ILinkDiscoveryService.class);
        l.add(IOFSwitchService.class);
        return l;
    }
 
    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {

        switchService = context.getServiceImpl(IOFSwitchService.class);
        // linkService = context.getServiceImpl(ILinkDiscoveryService.class);
        logger = LoggerFactory.getLogger(SCL.class);
        
        switches = new HashMap<String, IPv4Address>();
        ctrls = new ArrayList<String>();
        links = new ArrayList<Link>();
        hosts = new HashMap<String, IPv4Address>();
        intf2link = new HashMap<String, Link>();
        sw2link = new HashMap<String, Map<String, Link>>();
        networkGraph = new NetworkX();
        swToConn = new HashMap<String, IOFSwitch>();
        sw_tables = new HashMap<String, Map<String, Map<String, Link>>>();
        sw_tables_status = new HashMap<String, Map<String, Map<String, String>>>();
        EventCount = new ArrayList<Integer>(6);
        isStarted = false;

        for (int i = 0; i < 6; i++) EventCount.add(0);

        Map<String, String> configParameters = context.getConfigParams(this);
        String path = configParameters.get("path");

        try {
            String fmJson = new String(Files.readAllBytes(Paths.get(path)));
            loadTopology(fmJson);    
        } catch (IOException e) {
            logger.info("FAILED");
        }

        for (String sw : this.switches.keySet()) {    
            Map<String, Map<String, Link>> temp1 = new HashMap<String, Map<String, Link>>();
            Map<String, Map<String, String>> tempStatus1 = new HashMap<String, Map<String, String>>();

            for (String host1 : this.hosts.keySet()) {
                
                Map<String, Link> temp2 = new HashMap<String, Link>();
                Map<String, String> tempStatus2 = new HashMap<String, String>();

                for (String host2 : this.hosts.keySet()) {
            
                    if (host1.equals(host2)) continue;
                    
                    temp2.put(host2, null);
                    temp1.put(host1, temp2);
                    this.sw_tables.put(sw,temp1);

                    tempStatus2.put(host2, "");
                    tempStatus1.put(host1, tempStatus2);
                    this.sw_tables_status.put(sw,tempStatus1);
                }
            }
        }
    }

    public String switchID_to_string(DatapathId switchId) {
        String hexNumber = switchId.toString().replace(":", "");
        if (hexNumber.equals("0111111111111111")) return "s000"; // For Floodlight.
        Integer temp = Integer.parseInt(hexNumber, 16);

        if (temp < 10) return "s00" + Integer.toString(temp);
        if (temp < 100) return "s0" + Integer.toString(temp);
        return "s" + Integer.toString(temp);
    }

    @Override
    public void switchDeactivated(DatapathId switchId) {

    }

    @Override
    public void switchChanged(DatapathId switchId) {
        logger.info("Switch Changed: " + switchID_to_string(switchId));
    }

    // @Override
    // switchPortChanged(DatapathId switchId,OFPortDesc,PortChangeType,FloodlightContext)

    @Override
    public void switchPortChanged(DatapathId switchId,
                                  OFPortDesc port,
                                  PortChangeType type) {

        String swID = switchID_to_string(switchId);
        Link lnk = intf2link.get(swID + ':' + port.getName());
        boolean topoUpdated = false;

        if (lnk == null) return;

        logger.info("Port-" + type.toString() + ": " + swID + ':' + port.getName());

        String sw1 = lnk.sw1;
        String sw2 = lnk.sw2;

        if (type == PortChangeType.UP || type == PortChangeType.ADD) {
            EventCount.set(2, EventCount.get(2) + 1);

            if (sw1.equals(swID)) {
                lnk.state1 = 512;

                if (lnk.state2 == 1) {
                    return;
                } else {
                    topoUpdated = networkGraph.addEdge(sw1, sw2);
                }
            } else if (sw2.equals(swID)) {
                lnk.state2 = 512;

                if (lnk.state1 == 1) {
                    return;
                } else {
                    topoUpdated = networkGraph.addEdge(sw1, sw2);
                }
            }
        }

        if (type == PortChangeType.DOWN || type == PortChangeType.DELETE) {
            EventCount.set(3, EventCount.get(3) + 1);

            if (sw1.equals(swID)) {
                lnk.state1 = 1;

                if (lnk.state2 == 1) {
                    return;
                } else {
                    topoUpdated = networkGraph.removeEdge(sw1, sw2);
                }
            } else if (sw2.equals(swID)) {
                lnk.state2 = 1;

                if (lnk.state1 == 1) {
                    return;
                } else {
                    topoUpdated = networkGraph.removeEdge(sw1, sw2);
                }
            }
        }

        if (topoUpdated == true && isStarted == true) updateFlowEntries(calShortestRoute());
        logger.info("Event Count: " + EventCount.toString());
    }

    @Override
    public void switchActivated(DatapathId switchId) {
    }

    void updateFlowEntry(String switchName, String host1, String host2, Link lnk, String cmd) {
        IOFSwitch sw = swToConn.get(switchName);

        if (sw == null) return;

        OFFactory myFactory = sw.getOFFactory();

        IPv4Address nwSRC = hosts.get(host1);
        IPv4Address nwDST = hosts.get(host2);
        OFPort outPort;

        if (switchName.equals(lnk.sw1)) {
            outPort = OFPort.of(lnk.port1);
        } else {
            outPort = OFPort.of(lnk.port2);
        }

        Match myMatch = myFactory.buildMatch()
        .setExact(MatchField.ETH_TYPE, EthType.IPv4)
        .setExact(MatchField.IPV4_SRC, nwSRC)
        .setExact(MatchField.IPV4_DST, nwDST)
        .build();

        // OFInstructions instructions = myFactory.instructions();
        ArrayList<OFAction> actionList = new ArrayList<OFAction>();
        OFActions actions = myFactory.actions();

        OFActionOutput output = actions.buildOutput()
            // .setMaxLen(0xFFFFFFFF)
            .setPort(outPort)
            .build();
        actionList.add(output);
        
        
        //TODO: Solve this hack!
        if (cmd.equals("modify")) {
        	// logger.info("Modify: Switch: " + switchName + " , Host-1: " + host1 + ", Host-2: " + host2 + ", Outport: " + lnk.port1 + ", Outport: " + lnk.port2 + ",Entered: " + outPort.toString());

            OFFlowModify flowModify = myFactory.buildFlowModify()
                    .setBufferId(OFBufferId.NO_BUFFER)
                    .setPriority(50000)
                    .setMatch(myMatch)
                    // .setInstructions(myInstructionList)
                    .setOutPort(OFPort.of(65535))
                    .setActions(actionList)
                    // .setTableId(TableId.of(0))
                    .build();

            sw.write(flowModify);
        } else if (cmd.equals("delete")) {
        	// logger.info("Delete: Switch: " + switchName + " , Host-1: " + host1 + ", Host-2: " + host2 + ", Outport: " + lnk.port1 + ", Outport: " + lnk.port2 + ",Entered: " + outPort.toString());

            OFFlowDelete flowDelete = myFactory.buildFlowDelete()
                    .setBufferId(OFBufferId.NO_BUFFER)
                    .setPriority(50000)
                    .setMatch(myMatch)
                    // .setInstructions(myInstructionList)
                    .setOutPort(OFPort.of(65535))
                    .setActions(actionList)
                    // .setTableId(TableId.of(0))
                    .build();
                 
            sw.write(flowDelete);
        }     
    }

    void RandomFlow(String switchName) {
        Link lnk = new Link(switchName, switchName + "-eth10", switchName, switchName + "-eth10", 10, 10);
        updateFlowEntryOF14(switchName, "h001", "h001", lnk, "delete");
    }

    void updateFlowEntryOF14(String switchName, String host1, String host2, Link lnk, String cmd) {
        IOFSwitch sw = swToConn.get(switchName);

        if (sw == null) return;

        OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowDelete();

        OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
        List<OFAction> actions = new ArrayList<OFAction>();
        Match.Builder mb = sw.getOFFactory().buildMatch();

        OFFactory myFactory = sw.getOFFactory();

        IPv4Address nwSRC = hosts.get(host1);
        IPv4Address nwDST = hosts.get(host2);
        OFPort outPort;

        if (switchName.equals(lnk.sw1)) {
            outPort = OFPort.of(lnk.port1);
        } else {
            outPort = OFPort.of(lnk.port2);
        }

        mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
        .setExact(MatchField.IPV4_SRC, nwSRC)
        .setExact(MatchField.IPV4_DST, nwDST);

        actions.add(aob.setPort(outPort).setMaxLen(0xffFFffFF).build());

        fmb.setMatch(mb.build())
        .setBufferId(OFBufferId.NO_BUFFER)
        // .setOutPort(OFPort.of(0))
        .setPriority(50000)
        .setTableId(TableId.of(0));

        FlowModUtils.setActions(fmb, actions, sw);
        sw.write(fmb.build());

        if (cmd.equals("modify")) {
            fmb = sw.getOFFactory().buildFlowAdd();

            fmb.setMatch(mb.build())
            .setBufferId(OFBufferId.NO_BUFFER)
            // .setOutPort(OFPort.of(0))
            .setPriority(50000)
            .setTableId(TableId.of(0));

            FlowModUtils.setActions(fmb, actions, sw);
            sw.write(fmb.build());
        }
    }

    public void updateFlowEntries(Map<String, Map<String, List<Triples>>> updates) {
        if (updates.get("modify").size() == 0 && updates.get("delete").size() == 0) {
            return;
        }

        Iterator it = updates.get("modify").entrySet().iterator();
        logger.info("Flows Modified: " + updates.get("modify").entrySet().size());
        
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            String swName = (String) pair.getKey();
            

            for (Triples trp : (List <Triples>) pair.getValue()) {
                EventCount.set(4, EventCount.get(4) + 1);
                updateFlowEntryOF14(swName, trp.host1, trp.host2, trp.lnk, "modify");
            }
        }

        it = updates.get("delete").entrySet().iterator();
        logger.info("Flows Deleted: " + updates.get("delete").entrySet().size());

        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            String swName = (String) pair.getKey();
            
            for (Triples trp : (List <Triples>) pair.getValue()) {
                EventCount.set(5, EventCount.get(5) + 1);
                updateFlowEntryOF14(swName, trp.host1, trp.host2, trp.lnk, "delete");
            }
        }

        logger.info("Event Count: " + EventCount.toString());
    }

    public Map<String, Map<String, List<Triples>>> calShortestRoute() {
        // Integer current = 0;        
        Map<String, List<Triples>> tempMapModify = new HashMap<String, List<Triples>>();
        Map<String, List<Triples>> tempMapDelete = new HashMap<String, List<Triples>>();

        for (String sw : this.switches.keySet()) {
            tempMapModify.put(sw,new ArrayList<Triples>());
            tempMapDelete.put(sw,new ArrayList<Triples>());
        }

        Map<String, Map<String, List<Triples>>> updates = new HashMap<String, Map<String, List<Triples>>>();
        updates.put("modify", tempMapDelete);
        updates.put("delete", tempMapModify);

        this.networkGraph.FloydWarshallWithPathReconstruction();

        for (String host1 : this.hosts.keySet()) {
            for (String host2 : this.hosts.keySet()) {
                if (host1.equals(host2)) continue;
                // List<List<String>> paths = this.networkGraph.printAllPaths(host1, host2);
                // if (paths.size() == 0) continue;
                List<String> path = this.networkGraph.Path(host1, host2);
                if (path == null) continue;

                // List<String> path = paths.get(current % paths.size());
                // logger.info("Shortest Path: " + path.toString());
                // current += 1;

                for (int i = 1; i < path.size() - 1; i++) {
                    String a = path.get(i);
                    String b = path.get(i + 1);

                    Triples tempTriple = new Triples(host1, host2, this.sw2link.get(a).get(b));

                    if (this.sw_tables.get(a).get(host1).get(host2) != this.sw2link.get(a).get(b)) {
                        this.sw_tables.get(a).get(host1).put(host2,this.sw2link.get(a).get(b));
                        updates.get("modify").get(a).add(tempTriple);
                        this.sw_tables_status.get(a).get(host1).put(host2, "updated");
                    } else {
                        this.sw_tables_status.get(a).get(host1).put(host2, "checked");
                    }
                }
            }
        }

        for (String sw : this.sw_tables_status.keySet()) {
            for (String host1 : this.sw_tables_status.get(sw).keySet()) {
                for (String host2 : this.sw_tables_status.get(sw).get(host1).keySet()) {
                    if (this.sw_tables_status.get(sw).get(host1).get(host2).equals("to_be_deleted")) {
                    	this.sw_tables_status.get(sw).get(host1).put(host2, "");
                        updates.get("delete").get(sw).add(new Triples(host1, host2, this.sw_tables.get(sw).get(host1).get(host2)));
                        this.sw_tables.get(sw).get(host1).put(host2, null);
                    } else if ((this.sw_tables_status.get(sw).get(host1).get(host2).equals("checked")) || (this.sw_tables_status.get(sw).get(host1).get(host2).equals("updated"))) {
                    	this.sw_tables_status.get(sw).get(host1).put(host2, "to_be_deleted");
                    }
                }
            }
        }
        return updates;
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
        logger.info("Switch down: " + switchID_to_string(switchId));
        EventCount.set(1, EventCount.get(1) + 1);

        String swName = switchID_to_string(switchId);

        if (!this.networkGraph.hasNode(swName)) {
            logger.error("Switch not in current graph. Switch Name: " + swName);
            return;
        }

        this.networkGraph.removeNode(swName);
        swToConn.remove(swName);

        for (Link lnk : sw2link.get(swName).values()) {
            if (swName.equals(lnk.sw1)) lnk.state1 = 1;
            if (swName.equals(lnk.sw2)) lnk.state2 = 1; // TODO: REPLACE THESE NUMBERS WITH FLOODLLIGHT CONSTS.
        }

        if (isStarted == true) updateFlowEntries(calShortestRoute());

        logger.info("Event Count: " + EventCount.toString());
    }

    @Override
    public void switchAdded(DatapathId switchId) {
        logger.info("Switch up: " + switchID_to_string(switchId));
        EventCount.set(0, EventCount.get(0) + 1);

        String swName = switchID_to_string(switchId);

        if (this.switches.get(swName) == null) {
            logger.error("Switch not in topology. Switch Name: " + swName);
            return;
        }

        if (this.networkGraph.hasNode(swName)) {
            logger.error("Switch already in current graph. Switch Name: " + swName);
            return;
        }

        IOFSwitch sw = switchService.getActiveSwitch(switchId);
        this.networkGraph.addNode(swName);
        swToConn.put(swName, sw);

        // Get the enabled ports and start adding flow entries.

        // logger.info("Switch Added -- Now using the ports to update flow entries.");

        Collection<OFPort> portCollection = sw.getEnabledPortNumbers();
        boolean topoUpdates = false;

        for (OFPort port : portCollection) {
            EventCount.set(2, EventCount.get(2) + 1);
            // logger.info("Switch Port-ADD");
            // linkService.AddToSuppressLLDPs(switchId, port);

            String swID = switchID_to_string(switchId);
            String interfaceName = swToConn.get(swID).getPort(port).getName();
            Link lnk = intf2link.get(swID + ':' + interfaceName);

            if (lnk == null) continue;

            if (lnk.sw1.equals(swID)) {
                lnk.port1 = port.getPortNumber();
            } else if (lnk.sw2.equals(swID)) {
                lnk.port2 = port.getPortNumber();
            } else {
                // logger.info("WHY IS IT COMING HERE?");
            }

            // TODO: Old State Variable
            // TODO: lnk is None?

            String sw1 = lnk.sw1;
            String sw2 = lnk.sw2;

            if (sw1.equals(swID)) {
                lnk.state1 = 512;
                if (lnk.state2 == 512) this.networkGraph.addEdge(sw1, sw2);
                topoUpdates = true;
            } else if (sw2.equals(swID)) {
                lnk.state2 = 512;
                if (lnk.state1 == 512) this.networkGraph.addEdge(sw1, sw2);
                topoUpdates = true;
            }
        }

        if (topoUpdates == true && isStarted == true) updateFlowEntries(calShortestRoute());
        logger.info("Event Count: " + EventCount.toString());
    }

 
    @Override
    public void startUp(FloodlightModuleContext context) {
        // linkService.addListener(this);
        logger.info("STARTUP");


        switchService.addOFSwitchListener(this); 

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                	synchronized (mutex) {
	                    logger.info("GOING TO SLEEP ...");
	                    Thread.sleep(10000);
	                    logger.info("ADDING FLOWS ...");
	                    updateFlowEntries(calShortestRoute());
                        logger.info("DONE ADDING FLOWS ...");
	                }	
	                isStarted = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    isStarted = true;
                }
            }
        }).start();
    }
}