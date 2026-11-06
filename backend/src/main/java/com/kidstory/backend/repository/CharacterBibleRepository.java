package com.kidstory.backend.repository;

import com.kidstory.backend.entity.CharacterBible;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Çocuk yalnızca sistem şablonlarını ve kendine ait karakterleri kullanabilir. */
public interface CharacterBibleRepository extends JpaRepository<CharacterBible, Long> {

    @Query("select c from CharacterBible c where c.systemPreset = true or c.child.id = :childId order by c.systemPreset desc, c.id")
    List<CharacterBible> findAvailable(@Param("childId") Long childId);

    List<CharacterBible> findBySystemPresetTrueOrderById();
}