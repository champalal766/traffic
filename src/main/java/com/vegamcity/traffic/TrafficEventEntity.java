package com.vegamcity.traffic;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "traffic_events")
public class TrafficEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String roadName;
    @Column(nullable = false) private int vehicleCount;
    @Column(nullable = false) private double multiplier;
    @Column(nullable = false) private LocalDateTime createdAt;

    protected TrafficEventEntity() {}

    public TrafficEventEntity(String roadName, int vehicleCount, double multiplier) {
        this.roadName = roadName;
        this.vehicleCount = vehicleCount;
        this.multiplier = multiplier;
        this.createdAt = LocalDateTime.now();
    }

    public String getRoadName() { return roadName; }
    public int getVehicleCount() { return vehicleCount; }
}
