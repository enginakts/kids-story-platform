package com.kidstory.backend.service.ai;

import java.math.BigDecimal;

public interface TtsClient extends AiClient {

    SpeechResult synthesize(String text, String voiceId, int timeoutMs);

    /**
     * extension: mp3, wav. timestampsJson kelime zamanlamalarını taşır:
     * [{"word":"Güneş","start":0.0,"end":0.4}, ...]; sağlayıcı desteklemiyorsa boş dizi olabilir.
     */
    record SpeechResult(byte[] audio, String extension, BigDecimal durationSeconds, String timestampsJson) {
    }
}
