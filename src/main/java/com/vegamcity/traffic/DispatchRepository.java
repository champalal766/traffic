package com.vegamcity.traffic;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DispatchRepository extends JpaRepository<DispatchEntity, Long> {
    List<DispatchEntity> findTop20ByOrderByDispatchedAtDesc();
}
