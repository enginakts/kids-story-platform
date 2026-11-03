package com.kidstory.backend.repository;

import com.kidstory.backend.entity.Parent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParentRepository extends JpaRepository<Parent, Long> {

    @EntityGraph(attributePaths = "role")
    Optional<Parent> findByEmail(String email);

    boolean existsByEmail(String email);
}
