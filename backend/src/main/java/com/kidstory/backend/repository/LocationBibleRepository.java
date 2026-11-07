package com.kidstory.backend.repository;

import com.kidstory.backend.entity.LocationBible;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationBibleRepository extends JpaRepository<LocationBible, Long> {

    List<LocationBible> findBySystemPresetTrueOrderByIdAsc();
}
