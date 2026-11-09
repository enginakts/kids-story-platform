package com.kidstory.backend.repository;

import com.kidstory.backend.entity.StoryScene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StorySceneRepository extends JpaRepository<StoryScene, Long> {

    List<StoryScene> findByStoryIdOrderBySceneNumberAsc(Long storyId);

    /** Tüm sahnelerinin sesi hazır olan masallar (kütüphanede "Sesli" etiketi için). */
    @Query("select s.story.id from StoryScene s where s.story.parent.id = :parentId group by s.story.id having count(s) = count(s.audioUrl)")
    List<Long> findStoryIdsWithFullAudio(@Param("parentId") Long parentId);
}
