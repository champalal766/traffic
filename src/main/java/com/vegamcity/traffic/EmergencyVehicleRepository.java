package com.vegamcity.traffic;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmergencyVehicleRepository extends JpaRepository<EmergencyVehicleEntity, Long> {
    List<EmergencyVehicleEntity> findTop20ByOrderByCreatedAtDesc();
}
