package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Dış servis ve API anahtarı olmadan geliştirme taslakları. Üretimde yüklenmez.
 * Bir rolün gerçek anahtarı varsa o rolün taslağı yüklenmez: gerçek sağlayıcı hata verirse
 * sessizce taslağa düşülmesin, iş yeniden denensin.
 */
@Configuration
@Profile("!prod")
class StubClients {

    @Bean
    @ConditionalOnExpression("'${app.ai.featherless.api-key:}' == '' and '${app.ai.groq.api-key:}' == ''")
    LlmClient stubStoryText() {
        return new Stub("stub", AiProviderConfig.Type.LLM, """
                Paylaşınca Çoğalan Mutluluk

                Güneşli bir sabah ormanda küçük bir sepet dolusu çilek bulundu.

                Dostlar sepetin etrafında toplandı ve gülümsedi.

                Herkes çilekleri sırayla ve eşit paylaştı.

                Akşam olunca karınları tok, kalpleri sevinçle doluydu.""");
    }

    @Bean
    @ConditionalOnExpression("'${app.ai.gemini.api-key:}' == ''")
    LlmClient stubScenes() {
        return new Stub("stub-scene", AiProviderConfig.Type.SCENE, """
                {"title":"Paylaşınca Çoğalan Mutluluk","scenes":[
                {"text":"Güneşli bir sabah ormanda küçük bir sepet dolusu çilek bulundu.","image_prompt":"a basket of strawberries on soft grass in a sunny forest"},
                {"text":"Dostlar sepetin etrafında toplandı ve gülümsedi.","image_prompt":"friendly animals gathering around a basket of strawberries, smiling"},
                {"text":"Herkes çilekleri sırayla ve eşit paylaştı.","image_prompt":"animals sharing strawberries one by one, happy faces"},
                {"text":"Akşam olunca karınları tok, kalpleri sevinçle doluydu.","image_prompt":"animals resting together under a pastel sunset sky"}]}""");
    }

    /** Cloudflare anahtarı yoksa: prompt'a göre renklenen düz bir taslak resim (dış servis yok). */
    @Bean
    @ConditionalOnExpression("'${app.ai.cloudflare.api-token:}' == ''")
    ImageClient stubImage() {
        return new ImageClient() {
            @Override
            public String providerName() {
                return "stub";
            }

            @Override
            public AiProviderConfig.Type type() {
                return AiProviderConfig.Type.IMAGE;
            }

            @Override
            public ImageResult generate(ImageRequest request, AiProviderConfig config) {
                return new ImageResult(placeholder(request), "image/png");
            }
        };
    }

    /** Kâğıt zemin üzerinde, prompt'tan türeyen renkte bir elma ve yer: geliştirmede okuyucunun düzeni görülebilsin. */
    static byte[] placeholder(ImageClient.ImageRequest request) {
        int w = Math.max(64, request.width() / 4), h = Math.max(48, request.height() / 4);
        Color[] palette = {new Color(0xFF8C00), new Color(0xF24405), new Color(0x0E0D0D), new Color(0x2D6A3E)};
        Color main = palette[Math.floorMod(request.prompt().hashCode(), palette.length)];
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xF2F0E4));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0xE6E3D4));
        g.fillRect(0, h * 3 / 4, w, h / 4);
        g.setColor(main);
        int d = h / 2;
        g.fillOval(w / 2 - d / 2, h / 4, d, d);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Kikiri kapalıyken: metin uzunluğuna göre sessiz bir WAV. Kikiri açıksa yüklenmez (hata taslağa düşmesin). */
    @Bean
    @ConditionalOnExpression("!${app.ai.kikiri.enabled:true}")
    TtsClient stubTts() {
        return new TtsClient() {
            @Override
            public String providerName() {
                return "stub";
            }

            @Override
            public AiProviderConfig.Type type() {
                return AiProviderConfig.Type.TTS;
            }

            @Override
            public SpeechResult synthesize(SpeechRequest request, AiProviderConfig config) {
                double seconds = Math.min(20, 1 + request.text().length() / 15.0);
                return new SpeechResult(silentWav(seconds), "audio/wav",
                        BigDecimal.valueOf(seconds).setScale(2, RoundingMode.HALF_UP), null);
            }
        };
    }

    static byte[] silentWav(double seconds) {
        int rate = 8000;
        int samples = (int) (rate * seconds);
        ByteBuffer b = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + samples * 2).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples * 2);
        return b.array();
    }

    private record Stub(String providerName, AiProviderConfig.Type type, String answer) implements LlmClient {

        @Override
        public String complete(LlmRequest request, AiProviderConfig config) {
            return answer;
        }
    }
}
