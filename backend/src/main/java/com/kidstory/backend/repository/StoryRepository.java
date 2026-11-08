package com.kidstory.backend.repository;

import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.StoryEnums;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoryRepository extends JpaRepository<Story, Long> {

    @EntityGraph(attributePaths = "child")
    List<Story> findByParentIdOrderByCreatedAtDesc(Long parentId);

    @EntityGraph(attributePaths = "child")
    Optional<Story> findByIdAndParentId(Long id, Long parentId);

    long countByModerationStatus(StoryEnums.ModerationStatus status);
}
