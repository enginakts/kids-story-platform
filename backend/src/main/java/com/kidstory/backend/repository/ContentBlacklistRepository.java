package com.kidstory.backend.repository;

import com.kidstory.backend.entity.ContentBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentBlacklistRepository extends JpaRepository<ContentBlacklist, Long> {

    List<ContentBlacklist> findByActiveTrue();
}
