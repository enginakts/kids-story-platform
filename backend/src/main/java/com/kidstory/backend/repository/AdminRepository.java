package com.kidstory.backend.repository;

import com.kidstory.backend.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUsername(String username);
    Optional<Admin> findByEmail(String email);
    Optional<Admin> findByUsernameOrEmail(String username, String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
