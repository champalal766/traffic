package com.vegamcity.traffic;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "emergency_dispatches")
public class DispatchEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String vehicleId;

    @Column(nullable = false)
    private int severity;

    @Column(nullable = false)
    private String startJunction;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false)
    private double travelTime;

    @Column(nullable = false)
    private boolean routeFound;

    @Column(nullable = false)
    private LocalDateTime dispatchedAt;

    protected DispatchEntity() {
    }

    public DispatchEntity(String vehicleId, int severity, String startJunction, String destination,
                          String path, double travelTime, boolean routeFound) {
        this.vehicleId = vehicleId;
        this.severity = severity;
        this.startJunction = startJunction;
        this.destination = destination;
        this.path = path;
        this.travelTime = travelTime;
        this.routeFound = routeFound;
        this.dispatchedAt = LocalDateTime.now();
    }

    public String getVehicleId() { return vehicleId; }
    public int getSeverity() { return severity; }
    public String getStartJunction() { return startJunction; }
    public String getDestination() { return destination; }
    public String getPath() { return path; }
    public double getTravelTime() { return travelTime; }
    public boolean isRouteFound() { return routeFound; }
}
