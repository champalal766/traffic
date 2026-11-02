package com.vegamcity.traffic;

import jakarta.persistence.*;

@Entity
@Table(name = "roads")
public class RoadEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private int vehicleCount;

    protected RoadEntity() {
    }

    public RoadEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public void setVehicleCount(int vehicleCount) {
        this.vehicleCount = Math.max(0, vehicleCount);
    }
}
