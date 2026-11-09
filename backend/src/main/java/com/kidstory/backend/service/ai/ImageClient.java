package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;

import java.util.List;

public interface ImageClient extends AiClient {

    ImageResult generate(ImageRequest request, AiProviderConfig config);

    /**
     * references: karakter referans görselleri (JPEG, her biri 512 pikselden küçük). Prompt'ta "image 0", "image 1"
     * diye anılırlar. Referans desteklemeyen sağlayıcı/model bunları yok sayar.
     */
    record ImageRequest(String prompt, int width, int height, long seed, List<byte[]> references) {
    }

    record ImageResult(byte[] image, String mimeType) {
    }
}
