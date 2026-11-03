package com.kidstory.backend.repository;

import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.StoryEnums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {

    List<Story> findByParentIdOrderByCreatedAtDesc(Long parentId);

    /** Keşfet yalnızca onaylanıp yayımlanmış masalları gösterir. */
    List<Story> findTop50ByPublishedAtNotNullOrderByPublishedAtDesc();

    long countByModerationStatus(StoryEnums.ModerationStatus status);
}
