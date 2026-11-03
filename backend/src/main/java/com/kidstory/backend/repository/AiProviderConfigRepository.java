package com.kidstory.backend.repository;

import com.kidstory.backend.entity.AiProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, Long> {

    List<AiProviderConfig> findByProviderTypeAndActiveTrueOrderByPriorityAsc(AiProviderConfig.Type type);
}
