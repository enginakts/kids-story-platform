package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;

/** Yönlendiricinin her dış çağrı denemesini kaydettiği yer (AiUsageService). Kayıt hatası üretimi asla durdurmaz. */
public interface UsageRecorder {

    UsageRecorder NONE = (config, success, error, durationMs, reading, context) -> { };

    void record(AiProviderConfig config, boolean success, String error, long durationMs,
                UsageMeter.Reading reading, UsageMeter.Context context);
}
