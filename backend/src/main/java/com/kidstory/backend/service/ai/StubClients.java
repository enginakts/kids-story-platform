package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dış servis ve API anahtarı olmadan geliştirme taslakları. Üretimde yüklenmez.
 * Uçtan uca akışın denenebilmesi için anlamlı çıktı üretirler: geçerli masal JSON'u,
 * seed'e bağlı renkli görsel, kelime zamanlamalı sessiz ses.
 */
@Configuration
@Profile("!prod")
class StubClients {

    private static final String NAME = "stub";

    @Bean
    LlmClient stubLlm() {
        return new LlmClient() {
            @Override
            public String providerName() {
                return NAME;
            }

            @Override
            public AiProviderConfig.Type type() {
                return AiProviderConfig.Type.LLM;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt, int timeoutMs) {
                return StubStory.write(userPrompt);
            }
        };
    }

    @Bean
    ImageClient stubImage() {
        return new ImageClient() {
            @Override
            public String providerName() {
                return NAME;
            }

            @Override
            public AiProviderConfig.Type type() {
                return AiProviderConfig.Type.IMAGE;
            }

            @Override
            public GeneratedImage generate(String prompt, String negativePrompt, Long seed, int timeoutMs) {
                return new GeneratedImage(StubImage.draw(prompt, seed == null ? 0 : seed), "png");
            }
        };
    }

    @Bean
    TtsClient stubTts() {
        return new TtsClient() {
            @Override
            public String providerName() {
                return NAME;
            }

            @Override
            public AiProviderConfig.Type type() {
                return AiProviderConfig.Type.TTS;
            }

            @Override
            public SpeechResult synthesize(String text, String voiceId, int timeoutMs) {
                return StubSpeech.silent(text);
            }
        };
    }

    /** Prompt'taki satırlardan karakter, mekân ve duyguyu okuyup basit bir masal kurar. */
    static final class StubStory {
        private static final Locale TR = Locale.forLanguageTag("tr");

        static String write(String prompt) {
            String hero = field(prompt, "Ana karakter", "Minik dost").replaceAll("\\s*\\(.*$", "");
            String friends = field(prompt, "Yan karakterler", "-").replaceAll("\\s*\\([^)]*\\)", "");
            String friend = friends.equals("-") ? "yeni bir arkadaş" : friends;
            String place = field(prompt, "Mekân", "güzel bir yer").replaceAll("\\s*\\(.*$", "");
            String value = field(prompt, "Öğretilecek değer", "Paylaşma").toLowerCase(TR);
            String emotion = field(prompt, "Öğretilecek duygu", "Mutluluk");
            emotion = (emotion.equals("-") ? "mutluluk" : emotion).toLowerCase(TR);
            int count = Math.clamp(Integer.parseInt(field(prompt, "Sahne sayısı", "4").replaceAll("\\D", "")), 4, 6);

            List<String[]> scenes = new ArrayList<>();
            scenes.add(new String[] {hero + ", " + place + " içinde güne neşeyle uyanmış. Bugün çok güzel bir gün olacakmış.",
                    "waking up happily and stretching, bright smile"});
            if (count >= 5) {
                scenes.add(new String[] {hero + " yolda " + friend + " ile karşılaşmış. Birlikte oynamaya karar vermişler.",
                        "meeting a friend and waving hello, cheerful"});
            }
            scenes.add(new String[] {"Bir süre sonra küçük bir sorun çıkmış. " + hero + " derin bir nefes alıp düşünmüş.",
                    "looking thoughtfully at a small problem, calm expression"});
            scenes.add(new String[] {hero + ", " + value + " sayesinde sorunu çözmüş. İçini sıcacık bir " + emotion + " duygusu kaplamış.",
                    "solving the problem together, joyful and proud"});
            if (count == 6) {
                scenes.add(new String[] {"Arkadaşlar birbirlerine teşekkür etmiş. Herkes kocaman gülümsemiş.",
                        "hugging friends and saying thank you, warm smiles"});
            }
            scenes.add(new String[] {"Akşam olunca " + hero + " mutlulukla dinlenmiş. Yarın yeni güzellikler onu bekliyormuş.",
                    "resting peacefully with a content smile"});

            StringBuilder json = new StringBuilder("{\"title\":\"").append(escape(hero + " ve " + capitalize(value) + " Günü"))
                    .append("\",\"scenes\":[");
            for (int i = 0; i < scenes.size(); i++) {
                json.append(i == 0 ? "" : ",").append("{\"text\":\"").append(escape(scenes.get(i)[0]))
                        .append("\",\"imagePrompt\":\"").append(escape(scenes.get(i)[1])).append("\"}");
            }
            return json.append("]}").toString();
        }

        private static String field(String prompt, String name, String fallback) {
            Matcher m = Pattern.compile("^" + Pattern.quote(name) + ":\\s*(.+)$", Pattern.MULTILINE).matcher(prompt);
            return m.find() && !m.group(1).isBlank() ? m.group(1).trim() : fallback;
        }

        private static String capitalize(String s) {
            return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(TR) + s.substring(1);
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    /** Renk paleti seed'den gelir: aynı karakter tüm sahnelerde aynı renklerle çizilir. */
    static final class StubImage {
        static byte[] draw(String prompt, long seed) {
            Random palette = new Random(seed);
            Random scene = new Random(prompt.hashCode());
            int w = 768;
            int h = 512;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color sky = pastel(palette);
            Color ground = pastel(palette);
            Color body = pastel(palette).darker();
            g.setPaint(new GradientPaint(0, 0, sky, 0, h, Color.WHITE));
            g.fillRect(0, 0, w, h);
            g.setColor(ground);
            g.fillOval(-100, h - 160, w + 200, 320);
            int x = 180 + scene.nextInt(360);
            int y = 210 + scene.nextInt(60);
            g.setColor(body);
            g.fillOval(x - 70, y, 140, 150);
            g.fillOval(x - 55, y - 90, 110, 110);
            g.setColor(Color.WHITE);
            g.fillOval(x - 30, y - 55, 22, 22);
            g.fillOval(x + 8, y - 55, 22, 22);
            g.setColor(new Color(60, 50, 80));
            g.fillOval(x - 23, y - 48, 9, 9);
            g.fillOval(x + 15, y - 48, 9, 9);
            g.setStroke(new BasicStroke(4));
            g.drawArc(x - 20, y - 35, 40, 25, 200, 140);
            g.dispose();
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ImageIO.write(img, "png", out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static Color pastel(Random r) {
            return Color.getHSBColor(r.nextFloat(), 0.35f, 0.95f);
        }
    }

    /** Kelime başına 0,45 sn süren sessiz WAV ve buna uyan kelime zamanlamaları. */
    static final class StubSpeech {
        private static final int RATE = 8000;
        private static final double WORD_SECONDS = 0.45;

        static TtsClient.SpeechResult silent(String text) {
            String[] words = text.trim().split("\\s+");
            StringBuilder ts = new StringBuilder("[");
            for (int i = 0; i < words.length; i++) {
                ts.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "{\"word\":\"%s\",\"start\":%.2f,\"end\":%.2f}",
                        words[i].replace("\\", "\\\\").replace("\"", "\\\""), i * WORD_SECONDS, (i + 1) * WORD_SECONDS));
            }
            double seconds = words.length * WORD_SECONDS;
            return new TtsClient.SpeechResult(wav((int) (seconds * RATE)), "wav",
                    BigDecimal.valueOf(seconds).setScale(2, RoundingMode.HALF_UP), ts.append("]").toString());
        }

        private static byte[] wav(int samples) {
            ByteBuffer b = ByteBuffer.allocate(44 + samples).order(ByteOrder.LITTLE_ENDIAN);
            b.put("RIFF".getBytes()).putInt(36 + samples).put("WAVE".getBytes());
            b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                    .putInt(RATE).putInt(RATE).putShort((short) 1).putShort((short) 8);
            b.put("data".getBytes()).putInt(samples);
            for (int i = 0; i < samples; i++) {
                b.put((byte) 128);
            }
            return b.array();
        }
    }
}
