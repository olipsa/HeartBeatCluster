package cluster;

import service.Service;

import java.io.*;
import java.net.*;
import java.util.*;

public class ClusterClient {
    public static void main(String[] args) {
        String clusterNodeAddr = "localhost";
        int clusterNodePort;

        Scanner scanner = new Scanner(System.in);
        System.out.print("Introduceti portul TCP al nodului la care sa va conectati: ");

        try{
            clusterNodePort = Integer.parseInt(scanner.nextLine());
        }catch (NumberFormatException e){
            System.err.println("EROARE: Portul TCP trebuie sa fie un numar valid");
            return;
        }

        try (Socket socket = new Socket(clusterNodeAddr, clusterNodePort);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

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

            // Selectarea unui nod si a unui serviciu
            scanner = new Scanner(System.in);
            int nodeIndex = nodes.size();
            NodeInfo selectedNode = null;

            while(nodeIndex >= nodes.size()){
                System.out.print("Selectați nodul (index): ");
                try {
                    nodeIndex = scanner.nextInt();
                    selectedNode = nodes.get(nodeIndex);
                    System.out.println("Ati selectat nodul: " + selectedNode.nodeId);

                }catch (Exception e){
                    System.out.println("Ati selectat un index invalid");
                    scanner.nextLine();
                    nodeIndex = nodes.size();
                }
            }

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
                    serviceIndex = nodes.size();
                }
            }
            scanner.nextLine();
            System.out.print("Introduceti parametrul/parametrii de intrare pentru serviciu (separati prin spatiu, cu variabilele string intre \"): ");
            String parameters = scanner.nextLine();

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
                    // Trimite selectia la noul nod
                    newOut.writeObject("execute_service");
                    newOut.flush();
                    newOut.writeObject(selectedNode.nodeId);
                    newOut.flush();
                    newOut.writeObject(selectedService.getUid());
                    newOut.flush();
                    newOut.writeObject(parameters);
                    newOut.flush();

                    // Primeste răspunsul de la nodul selectat
                    String response = (String) newIn.readObject();
                    System.out.println(response);
                }
                catch (ConnectException e){
                    System.err.println("Nodul "+selectedNode.nodeId+" din cluster nu mai este activ");
                    return;
                }
                return;
            }

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
        catch (ConnectException e){
            System.err.println("Nodul "+clusterNodeAddr+":"+clusterNodePort+" din cluster nu mai este activ");
        }
        catch(IOException e){
            System.err.println("EROARE: Conexiunea nu s-a realizat cu " + clusterNodeAddr + " pe portul " + clusterNodePort);
            e.printStackTrace();
        }
        catch (ClassNotFoundException e){
            System.err.println("EROARE: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
