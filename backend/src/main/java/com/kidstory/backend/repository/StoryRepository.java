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

    /** Kaydedilmiş masallar (V16): geçici masallar listelerde görünmez. */
    @EntityGraph(attributePaths = "child")
    List<Story> findByParentIdAndSavedAtIsNotNullOrderByCreatedAtDesc(Long parentId);

    @EntityGraph(attributePaths = "child")
    List<Story> findByParentIdAndChildIdAndSavedAtIsNotNullOrderByCreatedAtDesc(Long parentId, Long childId);

    /** "Son üretilen": süresi dolmamış en yeni geçici masal. */
    @EntityGraph(attributePaths = "child")
    Optional<Story> findFirstByParentIdAndSavedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(Long parentId, java.time.Instant after);

    @EntityGraph(attributePaths = "child")
    Optional<Story> findFirstByParentIdAndChildIdAndSavedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(Long parentId, Long childId, java.time.Instant after);

    /** Başlık benzersizliği için ebeveynin diğer masallarının başlıkları. */
    @org.springframework.data.jpa.repository.Query("SELECT s.title FROM Story s WHERE s.parent.id = :parentId AND s.id <> :storyId")
    List<String> otherTitles(@org.springframework.data.repository.query.Param("parentId") Long parentId,
                             @org.springframework.data.repository.query.Param("storyId") Long storyId);

    /** Bir çocuğun masalları, en yeni başta. Ebeveyn kimliği de şarttır: başka ebeveynin çocuğunun masalı asla gelmez. */
    @EntityGraph(attributePaths = "child")
    List<Story> findByParentIdAndChildIdOrderByCreatedAtDesc(Long parentId, Long childId);

    long countByModerationStatus(StoryEnums.ModerationStatus status);
}
