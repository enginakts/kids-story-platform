package com.kidstory.backend.repository;

import com.kidstory.backend.entity.StoryCharacter;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoryCharacterRepository extends JpaRepository<StoryCharacter, Long> {

    @EntityGraph(attributePaths = "character")
    List<StoryCharacter> findByStoryIdOrderByIdAsc(Long storyId);
}
