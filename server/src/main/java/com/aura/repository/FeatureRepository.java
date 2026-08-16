package com.aura.repository;

import com.aura.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, UUID> {
    List<Feature> findByProjectId(UUID projectId);
    Optional<Feature> findByProjectIdAndKey(UUID projectId, String key);
    boolean existsByProjectIdAndKey(UUID projectId, String key);
}
