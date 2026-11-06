package com.kidstory.backend.service.ai;

public interface ImageClient extends AiClient {

    /** Aynı seed ve aynı rehber tanımlarıyla çağrılınca karakter sahneler arasında aynı kalmalıdır. */
    GeneratedImage generate(String prompt, String negativePrompt, Long seed, int timeoutMs);

    /** extension: png, webp, jpg. */
    record GeneratedImage(byte[] data, String extension) {
    }
}
