package com.kidstory.backend.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kidstory.backend.entity.AiProviderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Cloudflare Workers AI üzerinde Black Forest Labs FLUX modelleri. Model adı ai_providers_config'ten gelir:
 * FLUX.2 (ör. @cf/black-forest-labs/flux-2-klein-4b) multipart form ister, boyut ve referans görsel alır;
 * FLUX.1 schnell yalnızca JSON prompt alır (boyut ve referans yok). Cloudflare veri saklamaz (zero data retention).
 */
public class CloudflareImageClient implements ImageClient {

    public static final String NAME = "cloudflare";

    private final RestClient.Builder builder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final String accountId;
    private final String apiToken;

    public CloudflareImageClient(RestClient.Builder builder, String baseUrl, String accountId, String apiToken) {
        this.builder = builder;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.accountId = accountId;
        this.apiToken = apiToken;
    }

    @Override
    public String providerName() {
        return NAME;
    }

    @Override
    public AiProviderConfig.Type type() {
        return AiProviderConfig.Type.IMAGE;
    }

    @Override
    public ImageResult generate(ImageRequest request, AiProviderConfig config) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(config.getTimeoutMs()));
        RestClient client = builder.clone().requestFactory(factory).baseUrl(baseUrl).build();
        String model = config.getActiveModelName();
        String uri = "/accounts/" + accountId + "/ai/run/" + model;
        try {
            RestClient.RequestBodySpec spec = client.post().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken);
            Envelope envelope = (supportsMultipart(model)
                    ? spec.contentType(MediaType.MULTIPART_FORM_DATA).body(form(request))
                    : spec.contentType(MediaType.APPLICATION_JSON).body(Map.of("prompt", request.prompt(), "steps", 4)))
                    .retrieve().body(Envelope.class);
            if (envelope == null || !envelope.success() || envelope.result() == null || envelope.result().image() == null) {
                throw new IllegalStateException("Cloudflare görsel yanıtında görsel yok");
            }
            byte[] image = Base64.getDecoder().decode(envelope.result().image());
            if (image.length == 0) {
                throw new IllegalStateException("Cloudflare boş görsel döndü");
            }
            return new ImageResult(image, mimeType(image));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                // Günlük ücretsiz kota veya hız sınırı
                throw new RateLimitedException("Cloudflare görsel", OpenAiCompatibleLlmClient.retryAfter(e));
            }
            throw new IllegalStateException("Cloudflare görsel isteği başarısız: HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Cloudflare görsel servisine ulaşılamadı");
        }
    }

    static boolean supportsMultipart(String model) {
        return model.contains("flux-2");
    }

    private static MultiValueMap<String, Object> form(ImageRequest request) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("prompt", request.prompt());
        form.add("width", String.valueOf(request.width()));
        form.add("height", String.valueOf(request.height()));
        form.add("seed", String.valueOf(request.seed()));
        List<byte[]> refs = request.references() == null ? List.of() : request.references();
        for (int i = 0; i < Math.min(4, refs.size()); i++) {
            String name = "reference-" + i + ".jpg";
            form.add("input_image_" + i, new ByteArrayResource(refs.get(i)) {
                @Override
                public String getFilename() {
                    return name;
                }
            });
        }
        return form;
    }

    static String mimeType(byte[] image) {
        if (image.length > 3 && (image[0] & 0xFF) == 0x89 && image[1] == 'P' && image[2] == 'N' && image[3] == 'G') {
            return "image/png";
        }
        if (image.length > 11 && image[0] == 'R' && image[1] == 'I' && image[8] == 'W' && image[9] == 'E') {
            return "image/webp";
        }
        return "image/jpeg";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envelope(Result result, boolean success) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(String image) {
    }
}
