package com.kidstory.backend.repository;

import com.kidstory.backend.entity.StoryScene;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StorySceneRepository extends JpaRepository<StoryScene, Long> {

    List<StoryScene> findByStoryIdOrderBySceneNumberAsc(Long storyId);

    Optional<StoryScene> findByStoryIdAndSceneNumber(Long storyId, int sceneNumber);
}