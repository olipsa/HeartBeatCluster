package cluster;

import service.*;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ClusterNode {
    private static final int TIMEOUT_MILLIS = 5000;
    private static final int MULTICAST_PORT = 4446;
    private static int tcpPort;
    private static String nodeId;
    private static InetAddress group;
    private static DatagramSocket datagramSocket;
    private static MulticastSocket multicastSocket;

    private static Map<String, NodeInfo> topology = new ConcurrentHashMap<>();
    private static List<Service> services = new ArrayList<>();

    private static final List<Service> ALL_SERVICES = Arrays.asList(
            new Service("1", "adunare", "1.0", Arrays.asList("int", "int"), Arrays.asList("int"), new AddNumbersService()),
            new Service("2", "paritate", "2.1", Arrays.asList("int"), Arrays.asList("boolean"), new CheckEvenService()),
            new Service("3", "toUpper", "1.0", Arrays.asList("string"), Arrays.asList("string"), new UpperCaseService()),
            new Service("4", "repeatString", "1.0", Arrays.asList("string", "int"), Arrays.asList("string"), new RepeatStringService())
    );


    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("EROARE: Portul TCP nu a fost introdus ca argument");
            return;
        }

        try {
            tcpPort = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("EROARE: Portul TCP trebuie sa fie un numar valid");
            return;
        }

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(tcpPort);
            System.out.println("Nodul asculta pe portul TCP: " + tcpPort);
        } catch (BindException e) {
            System.err.println("EROARE: Portul TCP este deja folosit");
            return;
        }


        services = generateRandomServices();
        StringBuilder servicesData = new StringBuilder();
        for (Service service : services) {
            servicesData.append("uid=").append(service.getUid())
                    .append(",name=").append(service.getName())
                    .append(",version=").append(service.getVersion())
                    .append(",inputs=").append(String.join(":", service.getInputParams()))
                    .append(",returns=").append(String.join(":", service.getReturnParams()))
                    .append(";");
        }

        group = InetAddress.getByName("230.0.0.1");
        datagramSocket = new DatagramSocket();
        nodeId = "127.0.0.1:" + tcpPort;

        // Thread pentru trimiterea heartbeat-urilor
        new Thread(() -> {
            try {
                while (true) {
                    handleHeartbeatSend(servicesData);
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                datagramSocket.close();

            }
        }).start();

        multicastSocket = new MulticastSocket(MULTICAST_PORT);
        multicastSocket.joinGroup(group);

        // Thread pentru primirea heartbeat-urilor
        ServerSocket finalServerSocket = serverSocket;
        new Thread(() -> {
            try {
                while (true) {
                    handleHeartbeatReceive();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                datagramSocket.close();
                try {
                    finalServerSocket.close();
                    multicastSocket.leaveGroup(group);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                multicastSocket.close();
            }
        }).start();

        // Thread de actualizare a topologiei
        new Thread(() -> {
            while (true) {
                long currentTime = System.currentTimeMillis();

                topology.entrySet().removeIf(entry -> currentTime - entry.getValue().lastHeartbeat > TIMEOUT_MILLIS);

                System.out.println("\n-------------------------Noduri active-------------------------");
                topology.forEach((activeNodeId, info) ->
                        System.out.println("Nod activ: " + activeNodeId + ", Servicii: " + info.services)
                );
                System.out.println();

                try {
                    Thread.sleep(1000); // Actualizare la fiecare secundă
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();


        while (true) {
            // Thread pentru conexiuni TCP cu ClusterClient
            new Thread(() -> {
                try {
                    Socket clientSocket = finalServerSocket.accept();
                    handleClient(clientSocket);
                } catch (SocketException e) {
                    System.err.println("Clientul s-a deconectat");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
    }

    private static List<Service> generateRandomServices() {
        Random random = new Random();
        int numServices = random.nextInt(ALL_SERVICES.size()) + 1;

        List<Service> shuffledServices = new ArrayList<>(ALL_SERVICES);
        Collections.shuffle(shuffledServices);

        return shuffledServices.subList(0, numServices);
    }

    private static void handleHeartbeatReceive() throws IOException {
        byte[] buf = new byte[256];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        multicastSocket.receive(packet);

        // Extrage adresa si portul sender-ului din pachet

        String message = new String(packet.getData(), packet.getOffset(), packet.getLength()).trim();
        if (message.startsWith("HEARTBEAT")) {
            String[] parts = message.split(" ", 3); // Extrag "HEARTBEAT", nodeId si serviciile
            String senderNodeId = parts.length > 1 ? parts[1] : ""; // ID-ul nodului
            String senderServices = parts.length > 2 ? parts[2] : ""; // Serviciile trimise de nod

            // Actualizam lista de noduri
            NodeInfo senderNode = NodeInfo.fromString(senderNodeId, senderServices);
            topology.put(senderNodeId, senderNode);
            System.out.println("Heartbeat primit de la: " + senderNodeId + ", Servicii: " + senderNode.services);
        }
    }

    private static void handleHeartbeatSend(StringBuilder servicesData) throws IOException, InterruptedException {
        String message = "HEARTBEAT " + nodeId + " " + servicesData;
        byte[] buf = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MULTICAST_PORT);
        datagramSocket.send(packet);

        System.out.println("Trimis heartbeat: " + message);
        System.out.println();
        TimeUnit.SECONDS.sleep(3); // Interval de trimitere heartbeat
    }


    private static void handleClient(Socket clientSocket) throws IOException, ClassNotFoundException {
        try {
            ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());

            System.out.println("Client conectat: " + clientSocket.getInetAddress() + ":" + clientSocket.getLocalPort());

            // Citim tipul cererii
            String requestType = (String) in.readObject();
            System.out.println("Am primit request " + requestType);
            switch (requestType) {
                case "get_topology":
                    // Trimiterea nodurilor active catre client
                    out.writeObject(new ArrayList<>(topology.values()));
                    out.flush();
                    requestType = (String) in.readObject();
                    switch (requestType) {
                        case "close":
                            System.out.println("Client deconectat: " + clientSocket.getInetAddress() + ":" + clientSocket.getLocalPort());
                            clientSocket.close();
                            break;
                        case "execute_service":
                            handleExecuteService(in, out);
                            break;
                        default:
                            System.out.println("Tip cerere necunoscut");
                            out.writeObject("Eroare: Tip cerere necunoscut.");
                            out.flush();
                    }
                    break;
                case "execute_service":
                    // Executam serviciun selectat si trimitem raspuns catre client
                    handleExecuteService(in, out);
                    break;
                default:
                    System.out.println("Tip cerere necunoscut");
                    out.writeObject("Eroare: Tip cerere necunoscut.");
                    out.flush();
            }
        } catch (SocketException e) {
            System.err.println("Clientul s-a deconectat");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleExecuteService(ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        String selectedNodeId = (String) in.readObject();
        String serviceId = (String) in.readObject();
        String parameters = (String) in.readObject();
        List<String> parameterList = Arrays.asList(parameters.replaceAll("\\s+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", "").split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"));

        NodeInfo selectedNode = topology.get(selectedNodeId);
        if (selectedNode == null) {
            out.writeObject("Eroare: Nodul selectat nu mai este activ.");
            System.exit(1);
        }

        Optional<Service> selectedService = selectedNode.services.stream()
                .filter(service -> service.getUid().equals(serviceId))
                .findFirst();

        if (selectedService.isEmpty()) {
            out.writeObject("Eroare: Serviciul selectat nu este disponibil pe acest nod.");
            System.exit(1);
        }
        ThreadPoolExecutor serviceExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);

        serviceExecutor.submit(() -> {
            try {
                System.out.println("Execut serviciul: " + selectedService.get().getName() +
                        " cu parametrii de intrare " + parameters);

                String clientResponse = validateParameters(selectedService.get(), parameterList);
                if (!clientResponse.isEmpty()) {
                    System.out.println("Parametrii invalizi de intrare\n" + clientResponse);
                    out.writeObject(clientResponse);
                    out.flush();
                } else {
                    System.out.println("Parametrii de intrare validati\n");
                    String result = selectedService.get().execute(parameterList);

                    out.writeObject("Serviciul de " + selectedService.get().getName() +
                            " cu parametrii de intrare [" + parameters + "] a fost executat cu succes." +
                            "\nRezultat: " + result);
                    out.flush();
                }
                System.out.println("Raspuns trimis catre client.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static String validateParameters(Service service, List<String> parameters) {
        List<String> expectedTypes = service.getInputParams();
        String clientResponse = "";

        // Verificare numar parametrii
        if (parameters.size() != expectedTypes.size()) {
            return "Eroare: Numarul de parametrii introdus nu este corect. Serviciul necesita ca input un numar de " + expectedTypes.size() + " parametrii de intrare.\n";
        }

        // Verificare tip parametru
        StringBuilder clientResponseBuilder = new StringBuilder(clientResponse);
        for (int i = 0; i < parameters.size(); i++) {
            String param = parameters.get(i);
            String expectedType = expectedTypes.get(i);

            try {
                switch (expectedType) {
                    case "int":
                        Integer.parseInt(param);
                        break;
                    case "boolean":
                        if (!param.equalsIgnoreCase("true") && !param.equalsIgnoreCase("false")) {
                            clientResponseBuilder.append("Eroare: Parametrul \"").append(param).append("\" nu este de tip ").append(expectedType).append("\n");
                        }
                        break;
                    case "string":
                        if (param == null || param.isEmpty() || !(param.startsWith("\"") && param.endsWith("\""))) {
                            clientResponseBuilder.append("Eroare: Parametrul \"").append(param).append("\" nu este un string valid.\n");
                        }
                        break;
                    default:
                        System.err.println("Eroare: Tip necunoscut: " + expectedType);
                        System.exit(1);
                }
            } catch (NumberFormatException e) {
                clientResponseBuilder.append("Eroare: Parametrul \"").append(param).append("\" nu este de tip ").append(expectedType).append("\n");
            }
        }
        clientResponse = clientResponseBuilder.toString();
        return clientResponse;
    }

}

