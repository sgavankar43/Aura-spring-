package com.aura.repository;

import com.aura.entity.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {
    List<Environment> findByProjectIdOrderBySortOrderAsc(UUID projectId);
    Optional<Environment> findByProjectIdAndSlug(UUID projectId, String slug);
    boolean existsByProjectIdAndSlug(UUID projectId, String slug);
}
