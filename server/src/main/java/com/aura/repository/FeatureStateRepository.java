package com.aura.repository;

import com.aura.entity.FeatureState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureStateRepository extends JpaRepository<FeatureState, UUID> {
    Optional<FeatureState> findByFeatureIdAndEnvironmentId(UUID featureId, UUID environmentId);
    List<FeatureState> findByFeatureId(UUID featureId);
    List<FeatureState> findByEnvironmentId(UUID environmentId);
}
