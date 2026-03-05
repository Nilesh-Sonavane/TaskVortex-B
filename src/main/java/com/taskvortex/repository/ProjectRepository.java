package com.taskvortex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.taskvortex.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    boolean existsByProjectKey(String projectKey);

    List<Project> findByManagerId(Long managerId);

    List<Project> findAllByManagerIdOrMembersId(Long managerId, Long memberId);

    List<Project> findByMembersId(Long id);
}