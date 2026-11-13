package com.aura.repository;

import com.aura.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    List<ProjectMember> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    List<ProjectMember> findByEmail(String email);

    Optional<ProjectMember> findByProjectIdAndEmail(UUID projectId, String email);

    Optional<ProjectMember> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<ProjectMember> findByInviteToken(String inviteToken);

    long countByProjectIdAndRole(UUID projectId, String role);

    boolean existsByProjectIdAndEmail(UUID projectId, String email);
}
