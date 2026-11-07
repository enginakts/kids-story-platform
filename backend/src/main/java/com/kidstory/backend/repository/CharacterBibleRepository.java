package com.kidstory.backend.repository;

import com.kidstory.backend.entity.CharacterBible;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Ebeveyn karakterlerine her zaman parentId ile erişilir. */
public interface CharacterBibleRepository extends JpaRepository<CharacterBible, Long> {

    List<CharacterBible> findBySystemPresetTrueAndActiveTrueOrderByIdAsc();

    List<CharacterBible> findByParentIdAndActiveTrueOrderByCreatedAtDesc(Long parentId);

    Optional<CharacterBible> findByIdAndParentIdAndActiveTrue(Long id, Long parentId);

    long countByParentIdAndActiveTrue(Long parentId);

    /** Ebeveynin kullanabileceği karakter: kendi aktif karakteri ya da aktif sistem şablonu. */
    @Query("select c from CharacterBible c where c.id = :id and c.active = true"
            + " and (c.systemPreset = true or c.parent.id = :parentId)")
    Optional<CharacterBible> findUsable(@Param("id") Long id, @Param("parentId") Long parentId);
}
