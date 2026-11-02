package com.vegamcity.traffic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TrafficService {
    private static final Logger logger = LoggerFactory.getLogger(TrafficService.class);
    private final Map<String, Road> roads = new LinkedHashMap<>();
    private final RoadRepository roadRepository;
    private final EmergencyVehicleRepository emergencyVehicleRepository;
    private final TrafficEventRepository trafficEventRepository;
    private final List<SseEmitter> liveClients = new CopyOnWriteArrayList<>();
    private final Map<String, List<Edge>> cityGraph = new LinkedHashMap<>();
    private final Map<String, Double> trafficMap = new HashMap<>();
    private final Queue<String> trafficEventQueue = new LinkedList<>();
    private final PriorityQueue<EmergencyVehicle> emergencyQueue = new PriorityQueue<>();
    private final Map<String, Integer> junctionQueues = new LinkedHashMap<>();
    private final Map<String, Integer> initialTrafficPlan = new LinkedHashMap<>();
    private final Map<String, Integer> edgeCursors = new HashMap<>();
    private final List<FlowVehicle> inTransit = new ArrayList<>();
    private final Set<String> loggedClearedJunctions = new HashSet<>();
    /**
     * Road segments (e.g. "NorthJunction-EastJunction") currently traversed by
     * an active emergency vehicle.  Any flow whose segment key matches one of
     * these is held at the source — other segments flow normally.
     */
    private final Set<String> emergencySegments = new HashSet<>();
    /**
     * The final destination junction of the active emergency vehicle.
     * ALL traffic whose next hop lands at this junction is also halted so
     * the arrival zone is kept completely clear (destination-based halt).
     * Null when no emergency is active.
     */
    private String emergencyDestination = null;
    private int nextJunctionIndex;
    private long trafficPlanUpdatedAt;
    private boolean trafficPausedForEmergency;

    public TrafficService(RoadRepository roadRepository,
                          EmergencyVehicleRepository emergencyVehicleRepository,
                          TrafficEventRepository trafficEventRepository) {
        this.roadRepository = roadRepository;
        this.emergencyVehicleRepository = emergencyVehicleRepository;
        this.trafficEventRepository = trafficEventRepository;
        roads.put("Lotus Avenue", new Road("Lotus Avenue"));
        roads.put("Sunrise Boulevard", new Road("Sunrise Boulevard"));
        roads.put("Riverfront Road", new Road("Riverfront Road"));
        roads.put("Market Street", new Road("Market Street"));

        cityGraph.put("NorthJunction", List.of(
                new Edge("EastJunction", 5.0), new Edge("WestJunction", 5.0)));
        cityGraph.put("EastJunction", List.of(
                new Edge("NorthJunction", 5.0), new Edge("SouthJunction", 5.0)));
        cityGraph.put("SouthJunction", List.of(
                new Edge("EastJunction", 5.0), new Edge("WestJunction", 5.0)));
        cityGraph.put("WestJunction", List.of(
                new Edge("SouthJunction", 5.0), new Edge("NorthJunction", 5.0)));
        cityGraph.keySet().forEach(junction -> {
            junctionQueues.put(junction, 0);
            edgeCursors.put(junction, 0);
        });

        cityGraph.forEach((source, edges) -> edges.forEach(edge ->
                trafficMap.put(source + "-" + edge.target(), 1.0)));
        trafficEventQueue.add("NorthJunction-EastJunction:2.0");
        trafficEventQueue.add("EastJunction-SouthJunction:1.5");
        applyTrafficEvents();
    }

    @PostConstruct
    void loadPersistedState() {
        Map<String, String> renamedRoads = Map.of(
                "North Road", "Lotus Avenue",
                "South Road", "Sunrise Boulevard",
                "East Road", "Riverfront Road",
                "West Road", "Market Street");
        roads.values().forEach(road -> {
            Optional<RoadEntity> current = roadRepository.findByName(road.name());
            if (current.isPresent()) {
                road.setVehicleCount(current.get().getVehicleCount());
                initialTrafficPlan.put(road.name(), current.get().getVehicleCount());
                return;
            }
            renamedRoads.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(road.name()))
                    .findFirst()
                    .flatMap(entry -> roadRepository.findByName(entry.getKey()))
                    .ifPresentOrElse(old -> {
                        int previousCount = old.getVehicleCount();
                        road.setVehicleCount(previousCount);
                        initialTrafficPlan.put(road.name(), previousCount);
                        old.setVehicleCount(0);
                        roadRepository.save(old);
                        RoadEntity replacement = new RoadEntity(road.name());
                        replacement.setVehicleCount(previousCount);
                        roadRepository.save(replacement);
                    }, () -> {
                        initialTrafficPlan.put(road.name(), 0);
                        roadRepository.save(new RoadEntity(road.name()));
                    });
        });
    }

    public synchronized DashboardResponse dashboard() {
        List<RoadStatus> roadStatuses = roads.values().stream()
                .sorted(Comparator.comparingInt((Road road) -> road.vehicleCount()).reversed())
                .map(road -> new RoadStatus(road.name(), road.vehicleCount(), road.clearanceTime()))
                .toList();
        List<RoadStatus> initialPlan = roads.values().stream()
                .map(road -> new RoadStatus(road.name(),
                        initialTrafficPlan.getOrDefault(road.name(), road.vehicleCount()),
                        clearanceTime(initialTrafficPlan.getOrDefault(road.name(), road.vehicleCount()))))
                .sorted(Comparator.comparingInt((RoadStatus status) -> status.vehicleCount()).reversed())
                .toList();
        List<DispatchResult> history = emergencyVehicleRepository.findTop20ByOrderByCreatedAtDesc()
                .stream().map(TrafficService::toDispatchResult).toList();
        return new DashboardResponse(roadStatuses, history, emergencyQueue.size(),
                List.copyOf(cityGraph.keySet()), junctionStatuses(), flowSnapshots(), initialPlan,
                List.copyOf(emergencySegments), trafficPausedForEmergency, emergencyDestination);
    }

    public synchronized DashboardResponse updateTraffic(@Valid TrafficUpdateRequest request) {
        inTransit.clear();
        nextJunctionIndex = 0;
        request.vehicleCounts().forEach((roadName, count) -> {
            Road road = roads.get(roadName);
            if (road == null) {
                throw new IllegalArgumentException("Unknown road: " + roadName);
            }
            road.setVehicleCount(count);
            initialTrafficPlan.put(roadName, count);
            roadRepository.findByName(roadName).ifPresent(entity -> {
                entity.setVehicleCount(count);
                roadRepository.save(entity);
            });
            trafficEventRepository.save(new TrafficEventEntity(roadName, count, trafficMultiplier(count)));
            String junction = junctionForRoad(roadName);
            junctionQueues.put(junction, count);
            loggedClearedJunctions.remove(junction);
        });
        trafficPlanUpdatedAt = System.currentTimeMillis();
        DashboardResponse response = dashboard();
        publish(response);
        return response;
    }

    @Scheduled(fixedRate = 1000)
    public synchronized void advanceCityTraffic() {
        long now = System.currentTimeMillis();
        if (trafficPlanUpdatedAt > 0 && now - trafficPlanUpdatedAt < 1000L) {
            publish(dashboard());
            return;
        }
        Set<String> arrivedJunctions = new HashSet<>();
        inTransit.removeIf(vehicle -> {
            if (vehicle.arrivalAt() > now) return false;
            arrivedJunctions.add(vehicle.destination());
            return true;
        });

        List<String> junctionOrder = new ArrayList<>(cityGraph.keySet());
        for (int offset = 0; offset < junctionOrder.size(); offset++) {
            int junctionIndex = (nextJunctionIndex + offset) % junctionOrder.size();
            String source = junctionOrder.get(junctionIndex);
            if (arrivedJunctions.contains(source)) continue;
            Road sourceRoad = roadForJunction(source);
            int waiting = junctionQueues.getOrDefault(source, 0);
            if ((sourceRoad != null && sourceRoad.vehicleCount() <= 0) || waiting <= 0) {
                logJunctionCleared(source);
                continue;
            }
            List<Edge> outgoingEdges = cityGraph.get(source);
            int edgeIndex = edgeCursors.compute(source,
                    (junction, cursor) -> (cursor == null ? 0 : cursor) % outgoingEdges.size());
            Edge edge = outgoingEdges.get(edgeIndex);
            edgeCursors.put(source, (edgeIndex + 1) % outgoingEdges.size());

            // --- FIX: Block a vehicle batch if:
            //   1. Its specific segment is claimed by the emergency vehicle (path-based), OR
            //   2. Its target junction IS the emergency destination (destination-based halt —
            //      keeps the arrival zone clear so the emergency vehicle can reach it unobstructed).
            String segmentKey = source + "-" + edge.target();
            boolean segmentBlocked = emergencySegments.contains(segmentKey);
            boolean destBlocked = emergencyDestination != null
                    && edge.target().equals(emergencyDestination);
            if (segmentBlocked || destBlocked) {
                // Skip this tick for this segment; emergency vehicle has right-of-way.
                publish(dashboard());
                return;
            }

            int batch = 1;
            junctionQueues.put(source, waiting - batch);
            if (sourceRoad != null) {
                int remaining = Math.max(0, sourceRoad.vehicleCount() - batch);
                sourceRoad.setVehicleCount(remaining);
                roadRepository.findByName(sourceRoad.name()).ifPresent(entity -> {
                    entity.setVehicleCount(remaining);
                    roadRepository.save(entity);
                });
            }
            long travelMillis = Math.max(1000L, Math.round(edge.baseTravelTime() * 1000L));
            inTransit.add(new FlowVehicle(
                    UUID.randomUUID().toString(), source, edge.target(), batch,
                    now, now + travelMillis, false));
            nextJunctionIndex = (junctionIndex + 1) % junctionOrder.size();
            if (junctionQueues.get(source) == 0) {
                logJunctionCleared(source);
            }
            break;
        }
        publish(dashboard());
    }

    private void logJunctionCleared(String junction) {
        if (loggedClearedJunctions.add(junction)) {
            logger.info("Junction Cleared: {}", junction);
        }
    }

    public synchronized DispatchResult dispatch(@Valid EmergencyRequest request) {
        if (!roads.containsKey(request.road())) {
            throw new IllegalArgumentException("Unknown road: " + request.road());
        }
        if (!cityGraph.containsKey(request.destination())) {
            throw new IllegalArgumentException("Unknown destination junction.");
        }
        if (request.emergencyCount() <= 0) {
            throw new IllegalArgumentException("Add at least one emergency vehicle.");
        }

        for (int index = 1; index <= request.emergencyCount(); index++) {
            String vehicleId = request.emergencyCount() == 1
                    ? request.id() : request.id() + "-" + index;
            emergencyQueue.add(new EmergencyVehicle(
                    vehicleId, request.severity(), junctionForRoad(request.road()), request.destination()));
        }
        DispatchResult result = calculateNextDispatch();
        if (result.routeFound()) {
            // Path-based halt: mark every segment on the emergency route.
            List<String> path = result.path();
            emergencySegments.clear();
            for (int i = 0; i < path.size() - 1; i++) {
                emergencySegments.add(path.get(i) + "-" + path.get(i + 1));
            }
            // Destination-based halt: also block all flows whose next hop
            // targets the emergency vehicle's arrival junction. This keeps
            // the entire destination area clear for a safe, unobstructed arrival.
            emergencyDestination = result.destination();
            trafficPausedForEmergency = true;
        }
        emergencyVehicleRepository.save(new EmergencyVehicleEntity(result.vehicleId(), result.severity(), result.start(),
                result.destination(), String.join(" -> ", result.path()), result.travelTime(), result.routeFound()));
        publish(dashboard());
        return result;
    }

    public synchronized DashboardResponse resumeTraffic() {
        trafficPausedForEmergency = false;
        emergencySegments.clear();
        emergencyDestination = null;   // lift the destination-based halt
        DashboardResponse response = dashboard();
        publish(response);
        return response;
    }

    private static DispatchResult toDispatchResult(EmergencyVehicleEntity entity) {
        List<String> path = entity.getRoute().isBlank()
                ? List.of() : Arrays.asList(entity.getRoute().split(" -> "));
        return new DispatchResult(entity.getVehicleId(), entity.getSeverity(), entity.getStartJunction(),
                entity.getDestination(), path, entity.getTravelTime(), entity.isRouteFound());
    }

    public synchronized SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        liveClients.add(emitter);
        Runnable removeClient = () -> liveClients.remove(emitter);
        emitter.onCompletion(removeClient);
        emitter.onTimeout(removeClient);
        emitter.onError(exception -> {
            removeClient.run();
            logger.debug("Removing failed live traffic stream.", exception);
        });
        try {
                emitter.send(SseEmitter.event().name("traffic-update")
                    .data(java.util.Objects.requireNonNull(dashboard())));
        } catch (IOException exception) {
            removeClient.run();
            logger.debug("Removing live traffic stream that failed during subscription.", exception);
        }
        return emitter;
    }

    private void publish(DashboardResponse response) {
        for (SseEmitter emitter : liveClients) {
            try {
                emitter.send(SseEmitter.event().name("traffic-update")
                    .data(java.util.Objects.requireNonNull(response)));
            } catch (IOException exception) {
                liveClients.remove(emitter);
                logger.debug("Removing live traffic stream that failed during update.", exception);
            }
        }
    }

    private double trafficMultiplier(int vehicleCount) {
        return 1.0 + Math.min(vehicleCount, 100) / 20.0;
    }

    private double clearanceTime(int vehicleCount) {
        return vehicleCount == 0 ? 0.0 : vehicleCount * 2.0 + 3.0;
    }

    public synchronized RouteResult route(RouteRequest request) {
        if (!cityGraph.containsKey(request.start()) || !cityGraph.containsKey(request.destination())) {
            throw new IllegalArgumentException("Unknown start or destination junction.");
        }
        return findShortestRoute(request.start(), request.destination());
    }

    private String junctionForRoad(String road) {
        return switch (road) {
            case "Lotus Avenue" -> "NorthJunction";
            case "Sunrise Boulevard" -> "SouthJunction";
            case "Riverfront Road" -> "EastJunction";
            case "Market Street" -> "WestJunction";
            default -> throw new IllegalArgumentException("Unknown road: " + road);
        };
    }

    private Road roadForJunction(String junction) {
        return roads.values().stream()
                .filter(road -> junctionForRoad(road.name()).equals(junction))
                .findFirst()
                .orElse(null);
    }

    private DispatchResult calculateNextDispatch() {
        EmergencyVehicle vehicle = emergencyQueue.poll();
        RouteResult route = findShortestRoute(vehicle.currentLocation(), vehicle.destination());
        return new DispatchResult(vehicle.id(), vehicle.severity(), vehicle.currentLocation(),
                vehicle.destination(), route.path(), route.travelTime(), route.found());
    }

    private RouteResult findShortestRoute(String start, String destination) {
        PriorityQueue<RouteNode> minHeap = new PriorityQueue<>();
        Map<String, Double> minCost = new HashMap<>();
        Map<String, String> previousNode = new HashMap<>();
        cityGraph.keySet().forEach(node -> minCost.put(node, Double.MAX_VALUE));
        minCost.put(start, 0.0);
        minHeap.add(new RouteNode(start, 0.0));

        while (!minHeap.isEmpty()) {
            RouteNode current = minHeap.poll();
            if (current.junction().equals(destination)) break;
            if (current.cost() > minCost.get(current.junction())) continue;

            for (Edge edge : cityGraph.get(current.junction())) {
                String key = current.junction() + "-" + edge.target();
                double cost = current.cost() + edge.baseTravelTime() *
                        trafficMap.getOrDefault(key, 1.0);
                if (cost < minCost.get(edge.target())) {
                    minCost.put(edge.target(), cost);
                    previousNode.put(edge.target(), current.junction());
                    minHeap.add(new RouteNode(edge.target(), cost));
                }
            }
        }

        if (!start.equals(destination) && !previousNode.containsKey(destination)) {
            return new RouteResult(List.of(), 0.0, false);
        }
        List<String> path = new ArrayList<>();
        for (String node = destination; node != null; node = previousNode.get(node)) {
            path.add(node);
        }
        Collections.reverse(path);
        return new RouteResult(path, minCost.get(destination), true);
    }

    private void applyTrafficEvents() {
        while (!trafficEventQueue.isEmpty()) {
            String[] event = trafficEventQueue.poll().split(":");
            trafficMap.put(event[0], Double.parseDouble(event[1]));
        }
    }

    // ── Records & Request/Response types ────────────────────────────────────

    public record TrafficUpdateRequest(Map<String, @Min(0) Integer> vehicleCounts) {
        public TrafficUpdateRequest {
            if (vehicleCounts == null || vehicleCounts.isEmpty()) {
                throw new IllegalArgumentException("At least one road vehicle count is required.");
            }
        }
    }

    public record EmergencyRequest(
            @NotBlank String id,
            @Min(1) @Max(3) int severity,
            @NotBlank String road,
            @Min(0) int emergencyCount,
            @NotBlank String destination) {}

    public record RouteRequest(@NotBlank String start, @NotBlank String destination) {}
    public record RoadStatus(String name, int vehicleCount, double greenLightTime) {}
    public record DashboardResponse(
            List<RoadStatus> roads,
            List<DispatchResult> dispatches,
            int queuedEmergencyVehicles,
            List<String> junctions,
            List<JunctionStatus> junctionStatuses,
            List<FlowSnapshot> flowVehicles,
            List<RoadStatus> initialTrafficPlan,
            /** Segments currently blocked by an emergency vehicle (path-based halt). */
            List<String> emergencySegments,
            /** Whether traffic is currently paused due to an emergency dispatch. */
            boolean emergencyActive,
            /**
             * The destination junction of the active emergency vehicle.
             * All flows whose next hop targets this junction are also halted
             * (destination-based halt). Null / absent when no emergency is active.
             */
            String emergencyDestination) {}
    public record JunctionStatus(String name, int vehicleCount) {}
    public record FlowSnapshot(String id, String source, String destination, int vehicleCount,
                               long startedAt, long arrivalAt, boolean emergency) {}
    public record RouteResult(List<String> path, double travelTime, boolean found) {}
    public record DispatchResult(String vehicleId, int severity, String start, String destination,
                                 List<String> path, double travelTime, boolean routeFound) {}

    private record Edge(String target, double baseTravelTime) {}
    private record RouteNode(String junction, double cost) implements Comparable<RouteNode> {
        @Override public int compareTo(RouteNode other) {
            return Double.compare(cost, other.cost);
        }
    }
    private record EmergencyVehicle(String id, int severity, String currentLocation,
                                    String destination) implements Comparable<EmergencyVehicle> {
        @Override public int compareTo(EmergencyVehicle other) {
            return Integer.compare(severity, other.severity);
        }
    }
    private record FlowVehicle(String id, String source, String destination, int vehicleCount,
                               long startedAt, long arrivalAt, boolean emergency) {}

    private List<JunctionStatus> junctionStatuses() {
        return junctionQueues.entrySet().stream()
                .map(entry -> new JunctionStatus(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<FlowSnapshot> flowSnapshots() {
        return inTransit.stream()
                .map(vehicle -> new FlowSnapshot(vehicle.id(), vehicle.source(), vehicle.destination(),
                        vehicle.vehicleCount(), vehicle.startedAt(), vehicle.arrivalAt(), vehicle.emergency()))
                .toList();
    }

    private static final class Road {
        private final String name;
        private int vehicleCount;
        private Road(String name) { this.name = name; }
        private void setVehicleCount(int count) { vehicleCount = Math.max(0, count); }
        private String name() { return name; }
        private int vehicleCount() { return vehicleCount; }
        private double clearanceTime() { return vehicleCount == 0 ? 0.0 : vehicleCount * 2.0 + 3.0; }
    }
}
