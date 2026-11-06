package com.kidstory.backend.repository;

import com.kidstory.backend.entity.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    long countByStoryIdAndStatusNot(Long storyId, GenerationJob.Status status);

    long countByStoryIdAndStatus(Long storyId, GenerationJob.Status status);

    /** Masal kalıcı olarak başarısız olunca bekleyen işler iptal edilir. */
    @Modifying
    @Query("""
            update GenerationJob j set j.status = com.kidstory.backend.entity.GenerationJob.Status.FAILED,
                j.lastError = :reason, j.finishedAt = :now
            where j.story.id = :storyId and j.status = com.kidstory.backend.entity.GenerationJob.Status.QUEUED""")
    int cancelQueued(@Param("storyId") Long storyId, @Param("reason") String reason, @Param("now") Instant now);
}