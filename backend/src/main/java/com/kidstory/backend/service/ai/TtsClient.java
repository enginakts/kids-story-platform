package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;

import java.math.BigDecimal;

public interface TtsClient extends AiClient {

    SpeechResult synthesize(SpeechRequest request, AiProviderConfig config);

    /** speed: 1.0 normal hız; masal anlatımı için biraz daha yavaş değerler kullanılır. */
    record SpeechRequest(String text, String voiceId, double speed) {
    }

    /** timestampsJson kelime zamanlamalarını taşır; sağlayıcı desteklemiyorsa null olabilir. */
    record SpeechResult(byte[] audio, String mimeType, BigDecimal durationSeconds, String timestampsJson) {
    }
}
