package com.kidstory.backend.service.ai;

public interface ImageClient extends AiClient {

    byte[] generate(String prompt, String negativePrompt, Long seed, int timeoutMs);
}
