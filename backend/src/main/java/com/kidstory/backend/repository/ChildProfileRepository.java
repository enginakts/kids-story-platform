package com.kidstory.backend.repository;

import com.kidstory.backend.entity.ChildProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Tüm sorgular parentId içerir: veri izolasyonu depo düzeyinde zorunludur. */
public interface ChildProfileRepository extends JpaRepository<ChildProfile, Long> {

    List<ChildProfile> findByParentIdAndActiveTrueOrderByCreatedAtAsc(Long parentId);

    Optional<ChildProfile> findByIdAndParentIdAndActiveTrue(Long id, Long parentId);
}
