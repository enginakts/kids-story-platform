package com.kidstory.backend.repository;

import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.StoryEnums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Ebeveyn sorguları her zaman parentId içerir: başka ebeveynin masalı görünmez. */
public interface StoryRepository extends JpaRepository<Story, Long> {

    List<Story> findByParentIdOrderByCreatedAtDesc(Long parentId);

    List<Story> findByChildIdAndParentIdOrderByCreatedAtDesc(Long childId, Long parentId);

    Optional<Story> findByIdAndParentId(Long id, Long parentId);

    /** Keşfet yalnızca onaylanıp yayımlanmış masalları gösterir. */
    List<Story> findTop50ByPublishedAtNotNullOrderByPublishedAtDesc();

    long countByModerationStatus(StoryEnums.ModerationStatus status);
}
