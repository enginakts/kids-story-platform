package com.kidstory.backend.service;

import com.kidstory.backend.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kod değiştirmeden yönetilen ayarlar (system_settings). */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SystemSettingRepository settings;

    @Transactional(readOnly = true)
    public int intValue(String key, int fallback) {
        return settings.findBySettingKey(key)
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getSettingValue().trim());
                    } catch (NumberFormatException e) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }
}
