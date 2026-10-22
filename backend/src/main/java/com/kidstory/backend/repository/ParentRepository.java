package com.kidstory.backend.repository;

import com.kidstory.backend.entity.Parent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParentRepository extends JpaRepository<Parent, Long> {
    Optional<Parent> findByEmail(String email);
    boolean existsByEmail(String email);
}
