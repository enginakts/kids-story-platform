package com.kidstory.backend.service.ai;

public interface TtsClient extends AiClient {

    SpeechResult synthesize(String text, String voiceId, int timeoutMs);

    /** timestampsJson kelime zamanlamalarını taşır; sağlayıcı desteklemiyorsa boş olabilir. */
    record SpeechResult(byte[] audio, String timestampsJson) {
    }
}
