import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Scanner;

// ==========================================
// 1. ORIGINAL CODE LOGIC
// ==========================================
class Road {
    private String name;
    private int vehicleCount;

    public Road(String name) {
        this.name = name;
        this.vehicleCount = 0;
    }

    public void setVehicleCount(int count) {
        this.vehicleCount = Math.max(0, count);
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public String getName() {
        return name;
    }

    public double calculateClearanceTime() {
        if (this.vehicleCount == 0) return 0.0;
        return (this.vehicleCount * 2.0) + 3.0;
    }
}

// ==========================================
// 2. SMART CITY DATA STRUCTURES
// ==========================================
class EmergencyVehicle implements Comparable<EmergencyVehicle> {
    String id;
    int severity; 
    String currentLocation;
    String destination;

    public EmergencyVehicle(String id, int severity, String currentLocation, String destination) {
        this.id = id;
        this.severity = severity;
        this.currentLocation = currentLocation;
        this.destination = destination;
    }

    @Override
    public int compareTo(EmergencyVehicle other) {
        return Integer.compare(this.severity, other.severity); 
    }
}

class Edge {
    String targetNode;
    double baseTravelTime;

    public Edge(String targetNode, double baseTravelTime) {
        this.targetNode = targetNode;
        this.baseTravelTime = baseTravelTime;
    }
}

class RouteNode implements Comparable<RouteNode> {
    String junction;
    double totalTimeCost;

    public RouteNode(String junction, double totalTimeCost) {
        this.junction = junction;
        this.totalTimeCost = totalTimeCost;
    }

    @Override
    public int compareTo(RouteNode other) {
        return Double.compare(this.totalTimeCost, other.totalTimeCost); 
    }
}

// ==========================================
// 3. MAIN SYSTEM INTEGRATION
// ==========================================
public class _Main_ {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // --- A. INITIALIZE GRAPH ---
        Map<String, List<Edge>> cityGraph = new HashMap<>();
        cityGraph.put("CentralJunction", Arrays.asList(new Edge("Hospital", 10.0), new Edge("Bypass", 4.0)));
        cityGraph.put("Bypass", Arrays.asList(new Edge("Hospital", 5.0)));
        cityGraph.put("Hospital", new ArrayList<>());

        // --- B. INITIALIZE HASHMAP ---
        Map<String, Double> trafficMap = new HashMap<>();
        trafficMap.put("CentralJunction-Hospital", 1.0); 
        trafficMap.put("CentralJunction-Bypass", 1.0);   
        trafficMap.put("Bypass-Hospital", 1.0);          

        // --- C. INITIALIZE QUEUE ---
        Queue<String> trafficEventQueue = new LinkedList<>();
        trafficEventQueue.add("CentralJunction-Hospital:15.0"); 
        trafficEventQueue.add("CentralJunction-Bypass:1.0");

        while (!trafficEventQueue.isEmpty()) {
            String[] event = trafficEventQueue.poll().split(":");
            trafficMap.put(event[0], Double.parseDouble(event[1]));
        }

        // ==========================================
        // D. ORIGINAL MANUAL JUNCTION LOGIC
        // ==========================================
        List<Road> roads = new ArrayList<>();
        roads.add(new Road("North Road"));
        roads.add(new Road("South Road"));
        roads.add(new Road("East Road"));
        roads.add(new Road("West Road"));

        System.out.println("=== Vegam City: 4-Way Junction Traffic Controller ===");
        System.out.println("Please enter the number of waiting vehicles for each road:\n");

        for (Road road : roads) {
            System.out.print("Enter vehicle count for " + road.getName() + ": ");
            while (!scanner.hasNextInt()) {
                System.out.println("Invalid input! Please enter a valid whole number.");
                scanner.next(); 
                System.out.print("Enter vehicle count for " + road.getName() + ": ");
            }
            int count = scanner.nextInt();
            road.setVehicleCount(count);
        }

        System.out.println("\n---------------------------------------------------------");
        System.out.printf("%-15s | %-15s | %-15s%n", "Road Name", "Vehicles Waiting", "Green Light Time");
        System.out.println("---------------------------------------------------------");

        for (Road road : roads) {
            System.out.printf("%-15s | %-16d | %-5.1f seconds%n", 
                              road.getName(), road.getVehicleCount(), road.calculateClearanceTime());
        }
        System.out.println("---------------------------------------------------------");

        roads.sort((r1, r2) -> Integer.compare(r2.getVehicleCount(), r1.getVehicleCount()));

        System.out.println("\n=== Signal Execution Order (Highest Congestion First) ===");
        for (int i = 0; i < roads.size(); i++) {
            Road road = roads.get(i);
            if (road.getVehicleCount() > 0) {
                System.out.printf("Step %d: %s gets GREEN light for %.1f seconds.%n", 
                                  (i + 1), road.getName(), road.calculateClearanceTime());
            } else {
                System.out.printf("Step %d: %s has 0 vehicles -> SKIPPED (0.0 seconds).%n", 
                                  (i + 1), road.getName());
            }
        }

        // ==========================================
        // E. SMART CITY: DYNAMIC USER INPUT & ROUTING
        // ==========================================
        System.out.println("\n\n=== SMART CITY: EMERGENCY ROUTING OVERRIDE ===");
        
        PriorityQueue<EmergencyVehicle> emergencyQueue = new PriorityQueue<>();

        for (Road road : roads) {
            if (road.getVehicleCount() == 0) continue; 

            int evCount = 0;
            while (true) {
                System.out.print("\nHow many emergency vehicles are on " + road.getName() + "? (Total cars waiting: " + road.getVehicleCount() + "): ");
                
                // CRASH PROTECTION: Checks if input is a valid number before attempting to read it
                if (!scanner.hasNextInt()) {
                    System.out.println("[ERROR] Invalid input! Please enter a number.");
                    scanner.next(); // Clear the bad input
                    continue;
                }
                
                evCount = scanner.nextInt();
                scanner.nextLine(); 

                if (evCount > road.getVehicleCount()) {
                    System.out.println("[ERROR] You cannot have " + evCount + " emergency vehicles when there are only " + road.getVehicleCount() + " total vehicles. Try again.");
                } else if (evCount < 0) {
                    System.out.println("[ERROR] Count cannot be negative.");
                } else {
                    break; 
                }
            }

            for (int i = 1; i <= evCount; i++) {
                System.out.println("  --- " + road.getName() + " - Emergency Vehicle " + i + " ---");
                
                System.out.print("  Enter Vehicle ID (e.g., AMB-01): ");
                String id = scanner.nextLine();
                
                int severity = 0;
                while (true) {
                    System.out.print("  Enter Critical Level (1 = Highest/Ambulance, 3 = Lowest/Police): ");
                    
                    // CRASH PROTECTION: Validates severity input
                    if (!scanner.hasNextInt()) {
                        System.out.println("  [ERROR] Invalid input! Please enter a number.");
                        scanner.next();
                        continue;
                    }
                    severity = scanner.nextInt();
                    scanner.nextLine(); 
                    break;
                }
                
                System.out.print("  Enter Destination Node (e.g., Hospital, Bypass): ");
                String dest = scanner.nextLine().trim();
                
                // SMALL LETTER FIX: Automatically forces lowercase inputs to match the Graph
                if (dest.equalsIgnoreCase("hospital")) dest = "Hospital";
                if (dest.equalsIgnoreCase("bypass")) dest = "Bypass";
                
                emergencyQueue.add(new EmergencyVehicle(id + " (" + road.getName() + ")", severity, "CentralJunction", dest));
            }
        }

        if (emergencyQueue.isEmpty()) {
            System.out.println("\nNo emergency vehicles. Normal operations resume.");
        }

        while (!emergencyQueue.isEmpty()) {
            EmergencyVehicle ev = emergencyQueue.poll();
            System.out.println("\n=======================================================");
            System.out.println("Dispatching: " + ev.id + " | Severity: " + ev.severity);
            System.out.println("=======================================================");

            PriorityQueue<RouteNode> minHeap = new PriorityQueue<>(); 
            Map<String, Double> minCost = new HashMap<>();
            Map<String, String> previousNode = new HashMap<>();

            for (String node : cityGraph.keySet()) minCost.put(node, Double.MAX_VALUE);
            
            minCost.put(ev.currentLocation, 0.0);
            minHeap.add(new RouteNode(ev.currentLocation, 0.0));

            while (!minHeap.isEmpty()) {
                RouteNode current = minHeap.poll(); 
                String u = current.junction;

                if (u.equals(ev.destination)) break; 
                if (current.totalTimeCost > minCost.get(u)) continue; 

                for (Edge edge : cityGraph.get(u)) {
                    String v = edge.targetNode;
                    String roadKey = u + "-" + v;
                    
                    double trafficMultiplier = trafficMap.getOrDefault(roadKey, 1.0);
                    double edgeWeight = edge.baseTravelTime * trafficMultiplier;
                    double newCost = minCost.get(u) + edgeWeight;

                    if (newCost < minCost.get(v)) {
                        minCost.put(v, newCost);
                        previousNode.put(v, u);
                        minHeap.add(new RouteNode(v, newCost)); 
                    }
                }
            }

            if (!previousNode.containsKey(ev.destination) && !ev.currentLocation.equals(ev.destination)) {
                System.out.println("  -> ALERT: No valid route found to '" + ev.destination + "'. Check map spelling.");
                continue;
            }

            List<String> path = new ArrayList<>();
            String step = ev.destination;
            while (step != null) {
                path.add(step);
                step = previousNode.get(step);
            }
            Collections.reverse(path);
            
            System.out.println("  -> Optimal Route Found : " + String.join(" -> ", path));
            System.out.printf("  -> Estimated Travel Time: %.1f minutes%n", minCost.get(ev.destination));
        }

        scanner.close();
    }
}