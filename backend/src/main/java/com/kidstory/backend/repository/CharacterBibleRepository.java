package com.kidstory.backend.repository;

import com.kidstory.backend.entity.CharacterBible;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Karakterleri yöneticiler tanımlar (is_system_preset = TRUE, parent_id boş) ve bütün ebeveynler kullanır.
 * Ebeveynlerin V10'dan önce oluşturduğu karakterler yalnızca eski masallar için saklanır, seçilemez.
 */
public interface CharacterBibleRepository extends JpaRepository<CharacterBible, Long> {

    /** Ebeveynin masal sihirbazında seçebildikleri. */
    List<CharacterBible> findBySystemPresetTrueAndActiveTrueOrderByIdAsc();

    /** Yönetim listesi: yayındakiler önce, yayından kaldırılanlar sonra. */
    List<CharacterBible> findBySystemPresetTrueOrderByActiveDescIdAsc();

    Optional<CharacterBible> findByIdAndSystemPresetTrue(Long id);

    Optional<CharacterBible> findByIdAndSystemPresetTrueAndActiveTrue(Long id);

    boolean existsBySystemPresetTrueAndNameIgnoreCaseAndIdNot(String name, Long id);
}
