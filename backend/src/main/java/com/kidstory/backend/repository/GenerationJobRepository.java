package com.kidstory.backend.repository;

import com.kidstory.backend.entity.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    List<GenerationJob> findByStoryIdOrderByIdAsc(Long storyId);
}
