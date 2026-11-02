package com.vegamcity.traffic;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoadRepository extends JpaRepository<RoadEntity, Long> {
    Optional<RoadEntity> findByName(String name);
}
