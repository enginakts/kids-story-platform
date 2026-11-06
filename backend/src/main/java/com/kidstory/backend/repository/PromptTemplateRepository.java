package com.kidstory.backend.repository;

import com.kidstory.backend.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    Optional<PromptTemplate> findByTemplateKeyAndActiveTrue(String templateKey);
}