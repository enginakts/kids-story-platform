package com.kidstory.backend.repository;

import com.kidstory.backend.entity.StoryScene;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StorySceneRepository extends JpaRepository<StoryScene, Long> {

    List<StoryScene> findByStoryIdOrderBySceneNumberAsc(Long storyId);
}
