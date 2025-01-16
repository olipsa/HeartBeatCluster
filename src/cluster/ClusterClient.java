package cluster;

import service.Service;

import java.io.*;
import java.net.*;
import java.util.*;

public class ClusterClient {
    public static void main(String[] args) {
        String clusterNodeAddr = "localhost";
        int clusterNodePort = 0;
        boolean isPortSelected = false;
        boolean isClientConnected = false;
        Scanner scanner = new Scanner(System.in);


        while (!isClientConnected) {
            while (!isPortSelected) {
                System.out.print("Introduceti portul TCP al nodului la care sa va conectati: ");
                try {
                    clusterNodePort = Integer.parseInt(scanner.nextLine());
                    isPortSelected = true;
                } catch (NumberFormatException e) {
                    System.out.println("Portul TCP trebuie sa fie un numar valid!");
                }
            }

            try (Socket socket = new Socket(clusterNodeAddr, clusterNodePort);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                isClientConnected = true;
                out.writeObject("get_topology");
                out.flush();

                // Preluarea nodurilor active de la HeartbeatReceiver
                List<NodeInfo> nodes = (List<NodeInfo>) in.readObject();
                if (nodes.isEmpty()) {
                    System.out.println("Nu exista noduri active in cluster.");
                    return;
                }

                // Afișare nodurilor si a serviciilor
                System.out.println("Noduri active si servicii disponibile:");
                for (int i = 0; i < nodes.size(); i++) {
                    System.out.println(i + ": " + nodes.get(i));
                }


                scanner = new Scanner(System.in);
                int nodeIndex = nodes.size();
                NodeInfo selectedNode = null;
                // Selectarea unui nod si a unui serviciu
                while(nodeIndex >= nodes.size()){
                    System.out.print("Selectați nodul (index): ");
                    try {
                        nodeIndex = scanner.nextInt();
                        selectedNode = nodes.get(nodeIndex);
                        System.out.println("Ati selectat nodul: " + selectedNode.nodeId);

                    }catch (Exception e){
                        System.out.println("EROARE: Ati selectat un index invalid!");
                        scanner.nextLine();
                        nodeIndex = nodes.size();
                    }
                }

                String selectedNodeAddr = selectedNode.nodeId.split(":")[0];
                int selectedNodePort = Integer.parseInt(selectedNode.nodeId.split(":")[1]);

                if(clusterNodePort != selectedNodePort){
                    System.out.println("Conexiunea curenta este pe portul " + clusterNodePort +
                            ". Conectare la nodul selectat pe portul " + selectedNodePort + ".");

                    out.writeObject("close");
                    out.flush();
                    socket.close();

                    try (Socket newSocket = new Socket(selectedNodeAddr, selectedNodePort);
                         ObjectOutputStream newOut = new ObjectOutputStream(newSocket.getOutputStream());
                         ObjectInputStream newIn = new ObjectInputStream(newSocket.getInputStream())) {

                        System.out.println("Conectat la nodul "+selectedNode.nodeId+" din cluster.");

                        System.out.println("Servicii disponibile pe nodul selectat:");
                        for (int i = 0; i < selectedNode.services.size(); i++) {
                            System.out.println(i + ": " + selectedNode.services.get(i));
                        }

                        int serviceIndex = selectedNode.services.size();
                        Service selectedService = null;
                        while (serviceIndex >= selectedNode.services.size()) {
                            System.out.print("Selectati serviciul (index): ");
                            try {
                                serviceIndex = scanner.nextInt();
                                selectedService = selectedNode.services.get(serviceIndex);
                                System.out.println("Serviciul selectat: " + selectedService);
                            }catch (Exception e){
                                System.out.println("Ati selectat un index invalid");
                                scanner.nextLine();
                                serviceIndex = selectedNode.services.size();
                            }
                        }
                        scanner.nextLine();
                        System.out.print("Introduceti parametrul/parametrii de intrare pentru serviciu (separati prin virgula, iar pentru variabilele string intre \"\"): ");
                        String parameters = scanner.nextLine(); // validam parametrii pe server

                        // Trimite selectia la noul nod
                        newOut.writeObject("execute_service");
                        newOut.flush();
                        newOut.writeObject(selectedNode.nodeId);
                        newOut.flush();
                        newOut.writeObject(selectedService.getUid());
                        newOut.flush();
                        newOut.writeObject(parameters);
                        newOut.flush();

                        // Primeste raspunsul de la nodul selectat
                        String response = (String) newIn.readObject();
                        System.out.println(response);
                    }
                    catch (ConnectException e){
                        System.err.println("Nodul "+selectedNode.nodeId+" din cluster nu mai este activ");
                        return;
                    }
                    return;
                }
                else{
                    System.out.println("Conexiunea curenta este deja pe portul " + clusterNodePort);
                    // Suntem deja conectati la nodul selectat
                    System.out.println("Servicii disponibile pe nodul selectat:");
                    for (int i = 0; i < selectedNode.services.size(); i++) {
                        System.out.println(i + ": " + selectedNode.services.get(i));
                    }

                    int serviceIndex = selectedNode.services.size();
                    Service selectedService = null;
                    while (serviceIndex >= selectedNode.services.size()) {
                        System.out.print("Selectati serviciul (index): ");
                        try {
                            serviceIndex = scanner.nextInt();
                            selectedService = selectedNode.services.get(serviceIndex);
                            System.out.println("Serviciul selectat: " + selectedService);
                        }catch (Exception e){
                            System.out.println("Ati selectat un index invalid");
                            scanner.nextLine();
                            serviceIndex = selectedNode.services.size();
                        }
                    }
                    scanner.nextLine();
                    System.out.print("Introduceti parametrul/parametrii de intrare pentru serviciu (separati prin virgula, iar pentru variabilele string intre \"\"): ");
                    String parameters = scanner.nextLine();

                    // Trimite direct selecția la nodul initial, deoarece suntem deja conectati la el
                    out.writeObject("execute_service");
                    out.flush();
                    out.writeObject(selectedNode.nodeId);
                    out.flush();
                    out.writeObject(selectedService.getUid());
                    out.flush();
                    out.writeObject(parameters);
                    out.flush();

                    // Primeste raspunsul de la nod
                    String response = (String) in.readObject();
                    System.out.println(response);
                }
            }
            catch (ConnectException e){
                isPortSelected = false;
                System.out.println("EROARE: Nodul "+clusterNodeAddr+":"+clusterNodePort+" din cluster nu este activ");
            }
            catch(IOException e){
                isPortSelected = false;
                System.out.println("EROARE: Nodul "+clusterNodeAddr+":"+clusterNodePort+" din cluster nu este activ");
            }
            catch (ClassNotFoundException e){
                System.err.println("EROARE: " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }
    }
}
