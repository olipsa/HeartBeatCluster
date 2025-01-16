package cluster;

import service.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class NodeInfo  implements Serializable {
    private static final long serialVersionUID = 1L;
    public String nodeId;
    public int port;
    long lastHeartbeat;
    List<Service> services;

    public NodeInfo(String nodeId, long lastHeartbeat, List<Service> services) {
        this.nodeId = nodeId;
        this.lastHeartbeat = lastHeartbeat;
        this.services = services;
    }

    @Override
    public String toString() {
        return "NodeInfo{" +
                "nodeId='" + nodeId + '\'' +
                "lastHeartbeat=" + lastHeartbeat +
                ", services=" + services +
                '}';
    }
    public static NodeInfo fromString(String nodeId, String servicesData) {
        List<Service> serviceList = new ArrayList<>();
        if (!servicesData.isBlank()) {
            String[] serviceString = servicesData.split(";");
            for (String entry : serviceString) {
                String[] serviceParts = entry.split(",");
                String uid = serviceParts[0].split("=")[1];
                String name = serviceParts[1].split("=")[1];
                String version = serviceParts[2].split("=")[1];
                List<String> inputs = Arrays.asList(serviceParts[3].split("=")[1].split(":"));
                List<String> returns = Arrays.asList(serviceParts[4].split("=")[1].split(":"));

                ExecutableService nodeService = null;
                switch (name){
                    case "adunare":
                        nodeService = new AddNumbersService();
                        break;
                    case "paritate":
                        nodeService = new CheckEvenService();
                        break;
                    case "toUpper":
                        nodeService = new UpperCaseService();
                        break;
                    case "repeatString":
                        nodeService = new RepeatStringService();
                        break;
                    default:
                        break;
                }
                serviceList.add(new Service(uid, name, version, inputs, returns, nodeService));
            }
        }
        return new NodeInfo(nodeId, System.currentTimeMillis(), serviceList);
    }
}
