package com.aura.repository;

import com.aura.entity.GithubAppInstallation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GithubAppInstallationRepository extends JpaRepository<GithubAppInstallation, UUID> {
    List<GithubAppInstallation> findByAccountLoginIgnoreCase(String accountLogin);
    Optional<GithubAppInstallation> findByRepoFullNameIgnoreCase(String repoFullName);
    boolean existsByRepoFullNameIgnoreCase(String repoFullName);
    void deleteByInstallationId(Long installationId);
    void deleteByRepoFullNameIgnoreCase(String repoFullName);
}
