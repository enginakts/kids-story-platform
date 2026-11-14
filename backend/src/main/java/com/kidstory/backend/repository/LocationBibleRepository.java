package com.kidstory.backend.repository;

import com.kidstory.backend.entity.LocationBible;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Mekânları yöneticiler tanımlar (is_system_preset = TRUE) ve bütün ebeveynler kullanır. */
public interface LocationBibleRepository extends JpaRepository<LocationBible, Long> {

    /** Ebeveynin masal sihirbazında seçebildikleri. */
    List<LocationBible> findBySystemPresetTrueAndActiveTrueOrderByIdAsc();

    /** Yönetim listesi: yayındakiler önce, yayından kaldırılanlar sonra. */
    List<LocationBible> findBySystemPresetTrueOrderByActiveDescIdAsc();

    Optional<LocationBible> findByIdAndSystemPresetTrue(Long id);

    Optional<LocationBible> findByIdAndSystemPresetTrueAndActiveTrue(Long id);

    boolean existsBySystemPresetTrueAndNameIgnoreCaseAndIdNot(String name, Long id);
}
