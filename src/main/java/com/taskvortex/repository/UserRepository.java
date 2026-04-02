package com.taskvortex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taskvortex.entity.User;
import com.taskvortex.entity.UserRole;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(UserRole role);

    boolean existsByDepartmentId(Long id);

    @Query("SELECT DISTINCT u FROM User u WHERE u.id IN " +
            "(SELECT t.assigneeId FROM Task t WHERE t.project.manager.id = :managerId)")
    List<User> findTeamMembersByManagerId(@Param("managerId") Long managerId);

}
