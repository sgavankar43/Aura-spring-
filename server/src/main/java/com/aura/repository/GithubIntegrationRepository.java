package com.aura.repository;

import com.aura.entity.GithubIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GithubIntegrationRepository extends JpaRepository<GithubIntegration, UUID> {
    Optional<GithubIntegration> findByProjectId(UUID projectId);
    boolean existsByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
