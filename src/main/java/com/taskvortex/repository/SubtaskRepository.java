package com.taskvortex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taskvortex.entity.Subtask;

@Repository
public interface SubtaskRepository extends JpaRepository<Subtask, Long> {
}