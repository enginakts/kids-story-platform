package com.kidstory.backend.repository;

import com.kidstory.backend.entity.ChildProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChildProfileRepository extends JpaRepository<ChildProfile, Long> {
    List<ChildProfile> findByParentIdAndIsActiveTrue(Long parentId);
    List<ChildProfile> findByParentId(Long parentId);
}
