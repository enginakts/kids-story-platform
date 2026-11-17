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

                %s

                %s

                %s

                %s""".formatted(STUB_SCENES[0], STUB_SCENES[1], STUB_SCENES[2], STUB_SCENES[3]));
    }

    /** Taslak masal (anahtar yokken): 4 sahne, giriş-gelişme-sonuç, her sahne 3-6 kısa cümle, son cümle ders. */
    private static final String[] STUB_SCENES = {
            "Bir varmış, bir yokmuş, Çiçekli Orman'da minik bir tavşan yaşarmış. Her sabah papatyaların arasında zıplar, kuşlara günaydın dermiş. O sabah güneş ılık ılık parlıyormuş. Tavşan ağaçların dibinde kırmızı bir parıltı görmüş. Yaklaşınca küçük bir sepet dolusu çilek bulmuş. Gözleri sevinçle parlamış.",
            "Az sonra sincap, kirpi ve kuş da gelmiş. Herkes çileklere bakıp gülümsemiş. Ama sepette yalnızca dört çilek varmış. Tavşan bir an düşünmüş: “Hepimize yeter mi acaba?” Önce çilekleri yan yana dizmiş. Sonra arkadaşlarını tek tek saymış. Dört dost, dört çilek! Bu yüzden içi rahatlamış.",
            "Tavşan her arkadaşına birer çilek uzatmış. Sincap teşekkür edip kuyruğunu sallamış. Kirpi çileğini küçük küçük ısırmış. Kuş ise neşeyle şarkı söylemeye başlamış. Paylaştıkça orman daha da şenlenmiş. Tavşan kendi çileğini yerken hiç olmadığı kadar mutlu hissetmiş.",
            "Akşam olunca gökyüzü pembeye boyanmış. Dostlar yumuşak çimenlere uzanıp yıldızları seyretmiş. Karınları tok, kalpleri sevinçle doluymuş. Tavşan gözlerini yavaşça kapatmış ve gülümseyerek uykuya dalmış. Yanında arkadaşları da mışıl mışıl uyuyormuş. Tavşan, paylaştıkça mutluluğun çoğaldığını öğrenmiş."};

    @Bean
    @ConditionalOnExpression("'${app.ai.gemini.api-key:}' == ''")
    LlmClient stubScenes() {
        return new Stub("stub-scene", AiProviderConfig.Type.SCENE, """
                {"title":"Paylaşınca Çoğalan Mutluluk","scenes":[
                {"text":"%s","image_prompt":"a little rabbit finding a basket of strawberries on soft grass in a sunny forest"},
                {"text":"%s","image_prompt":"a rabbit, a squirrel, a hedgehog and a bird counting four strawberries together"},
                {"text":"%s","image_prompt":"animal friends happily eating one strawberry each while a bird sings"},
                {"text":"%s","image_prompt":"animal friends sleeping on the grass under a pink evening sky with first stars"}]}"""
                .formatted(STUB_SCENES[0], STUB_SCENES[1], STUB_SCENES[2], STUB_SCENES[3]));
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
