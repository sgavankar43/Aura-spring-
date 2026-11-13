package com.aura.repository;

import com.aura.entity.ChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, UUID> {
    List<ChangeRequest> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    List<ChangeRequest> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, String status);
    List<ChangeRequest> findByProjectIdAndFeatureIdAndEnvironmentIdAndStatus(UUID projectId, UUID featureId, UUID environmentId, String status);
    long countByProjectIdAndStatus(UUID projectId, String status);

    @Modifying
    @Query("DELETE FROM ChangeRequest cr WHERE cr.feature.id = :featureId")
    void deleteByFeatureId(@Param("featureId") UUID featureId);
}
